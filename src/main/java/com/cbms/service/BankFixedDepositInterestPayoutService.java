package com.cbms.service;

import com.cbms.dto.BankFixedDepositInterestPayoutRequest;
import com.cbms.dto.BankFixedDepositInterestPayoutResponse;
import com.cbms.entity.BankAccount;
import com.cbms.entity.BankAccountBalance;
import com.cbms.entity.BankLedgerAccount;
import com.cbms.entity.BankLedgerEntry;
import com.cbms.entity.BankTransaction;
import com.cbms.repository.BankAccountRepository;
import com.cbms.repository.BankAccountBalanceRepository;
import com.cbms.repository.BankLedgerAccountRepository;
import com.cbms.repository.BankLedgerEntryRepository;
import com.cbms.repository.BankTransactionRepository;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BankFixedDepositInterestPayoutService {

    private final JdbcTemplate jdbc;
    private final BankAccountRepository accountRepository;
    private final BankAccountBalanceRepository balanceRepository;
    private final BankTransactionRepository transactionRepository;
    private final BankLedgerAccountRepository glRepository;
    private final BankLedgerEntryRepository entryRepository;

    public BankFixedDepositInterestPayoutService(
            JdbcTemplate jdbc,
            BankAccountRepository accountRepository,
            BankAccountBalanceRepository balanceRepository,
            BankTransactionRepository transactionRepository,
            BankLedgerAccountRepository glRepository,
            BankLedgerEntryRepository entryRepository
    ) {
        this.jdbc = jdbc;
        this.accountRepository = accountRepository;
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.glRepository = glRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional
    public BankFixedDepositInterestPayoutResponse payout(
            Long fdId,
            BankFixedDepositInterestPayoutRequest request,
            Authentication authentication
    ) {
        if (fdId == null || fdId <= 0) {
            throw badRequest("Invalid fixed deposit ID");
        }

        if (request == null || request.destinationAccountId() == null) {
            throw badRequest("Destination account is required");
        }

        // Lock FD first. This serializes payout requests
        // for the same fixed deposit.
        List<Map<String, Object>> deposits = jdbc.queryForList(
                """
                SELECT fd_id, cust_id, fd_sts, principal_amt
                FROM public.bank_fixed_deposit
                WHERE fd_id = ?
                FOR UPDATE
                """,
                fdId
        );

        if (deposits.isEmpty()) {
            throw notFound("Fixed deposit not found");
        }

        Map<String, Object> fd = deposits.get(0);

        String fdStatus = (String) fd.get("fd_sts");

        if (!"ACTIVE".equals(fdStatus)
                && !"MATURED".equals(fdStatus)) {
            throw conflict(
                    "Only ACTIVE or MATURED deposits can receive interest payouts"
            );
        }

        Long customerId = ((Number) fd.get("cust_id")).longValue();
        BigDecimal principal = (BigDecimal) fd.get("principal_amt");

        // Verify posted funding and GL liability for normal placements or renewals.
        Long fundingCount = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.bank_fixed_deposit_placement p
                JOIN public.bank_transaction t
                  ON t.txn_ref = p.posting_ref
                WHERE p.fd_id = ?
                  AND p.placement_sts = 'POSTED'
                  AND p.placement_amt = ?
                  AND p.ccy_cd = 'MYR'
                  AND p.funding_typ IN ('FD_PLACEMENT', 'FD_RENEWAL')
                  AND t.txn_typ = p.funding_typ
                  AND t.txn_sts = 'POSTED'
                  AND t.acct_id = p.source_acct_id
                  AND t.txn_amt = p.placement_amt
                  AND t.ccy_cd = p.ccy_cd
                  AND EXISTS (
                      SELECT 1
                      FROM public.bank_ledger_entry e
                      JOIN public.bank_ledger_account g
                        ON g.gl_id = e.gl_id
                      WHERE e.txn_id = t.txn_id
                        AND g.gl_cd = '200200'
                        AND e.entry_side = 'CREDIT'
                        AND e.entry_amt = p.placement_amt
                  )
                  AND (
                      p.funding_typ = 'FD_PLACEMENT'
                      OR EXISTS (
                          SELECT 1
                          FROM public.bank_fixed_deposit_renewal r
                          JOIN public.bank_fixed_deposit original
                            ON original.fd_id = r.original_fd_id
                          JOIN public.bank_fixed_deposit_maturity m
                            ON m.fd_id = r.original_fd_id
                          WHERE r.renewed_fd_id = p.fd_id
                            AND r.renewal_sts = 'PROCESSED'
                            AND r.renewal_typ = 'PRINCIPAL_ONLY'
                            AND r.renewal_amt = p.placement_amt
                            AND r.principal_amt = original.principal_amt
                            AND r.capitalized_interest = 0
                            AND original.fd_sts = 'MATURED'
                            AND m.maturity_sts = 'PROCESSED'
                            AND EXISTS (
                                SELECT 1
                                FROM public.bank_ledger_entry e
                                JOIN public.bank_ledger_account g
                                  ON g.gl_id = e.gl_id
                                WHERE e.txn_id = t.txn_id
                                  AND g.gl_cd = '200200'
                                  AND e.entry_side = 'DEBIT'
                                  AND e.entry_amt = r.principal_amt
                            )
                      )
                  )
                """,
                Long.class,
                fdId,
                principal
        );

        if (fundingCount == null || fundingCount != 1) {
            throw conflict("Fixed deposit funding is not verified");
        }

        // Lock the destination account.
        Long destinationId = request.destinationAccountId();

        BankAccount destination = accountRepository
                .findByIdForUpdate(destinationId)
                .orElseThrow(() -> notFound("Destination account not found"));

        if (!customerId.equals(destination.getCustId())) {
            throw conflict("Destination account belongs to another customer");
        }

        if (!"ACTIVE".equals(destination.getAcctSts())) {
            throw conflict("Destination account must be ACTIVE");
        }

        if (!"MYR".equals(destination.getCcyCd())) {
            throw conflict("Only MYR payouts are supported");
        }

        if (!"SAVINGS".equals(destination.getAcctTyp())
                && !"CURRENT".equals(destination.getAcctTyp())) {
            throw conflict(
                    "Payout requires a SAVINGS or CURRENT account"
            );
        }

        BankAccountBalance balance = balanceRepository
                .findByIdForUpdate(destinationId)
                .orElseThrow(() ->
                        conflict("Destination account balance missing")
                );

        // Lock all currently unpaid accruals.
        // The unique accrual_id in the payout item table
        // provides an additional database safeguard.
        List<Map<String, Object>> unpaid = jdbc.queryForList(
                """
                SELECT a.accrual_id, a.accrued_interest
                FROM public.bank_fixed_deposit_accrual a
                WHERE a.fd_id = ?
                  AND a.accrual_sts = 'POSTED'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM public.bank_fixed_deposit_interest_payout_item i
                      WHERE i.accrual_id = a.accrual_id
                  )
                ORDER BY a.accrual_dt
                FOR UPDATE OF a
                """,
                fdId
        );

        if (unpaid.isEmpty()) {
            throw conflict("No unpaid interest accruals found");
        }

        BigDecimal amount = BigDecimal.ZERO;

        for (Map<String, Object> row : unpaid) {
            amount = amount.add(
                    (BigDecimal) row.get("accrued_interest")
            );
        }

        // No zero-amount financial transaction.
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw conflict("No positive accrued interest available");
        }

        if (amount.scale() > 2
                || amount.precision() - amount.scale() > 17) {
            throw conflict("Payout amount exceeds supported precision");
        }

        BankLedgerAccount payableGl = findGl("200300");
        BankLedgerAccount depositGl = findGl("200100");

        OffsetDateTime now = OffsetDateTime.now();
        String payoutRef = "FD-PAY-" + UUID.randomUUID();

        BankTransaction txn = new BankTransaction();
        txn.setTxnRef("TXN-" + UUID.randomUUID());
        txn.setAcctId(destinationId);
        txn.setDestAcctId(null);
        txn.setTxnTyp("FD_INTEREST_PAYOUT");
        txn.setTxnAmt(amount);
        txn.setCcyCd("MYR");
        txn.setTxnSts("POSTED");
        txn.setIdempotencyKey(payoutRef);
        txn.setCrtDt(now);
        txn.setCrtBy(authentication.getName());

        txn = transactionRepository.saveAndFlush(txn);

        // Debit interest payable liability.
        BankLedgerEntry debit = new BankLedgerEntry();
        debit.setTxnId(txn.getTxnId());
        debit.setGlId(payableGl.getGlId());
        debit.setAcctId(null);
        debit.setEntrySide("DEBIT");
        debit.setEntryAmt(amount);
        debit.setCrtDt(now);

        // Credit customer deposits liability.
        BankLedgerEntry credit = new BankLedgerEntry();
        credit.setTxnId(txn.getTxnId());
        credit.setGlId(depositGl.getGlId());
        credit.setAcctId(destinationId);
        credit.setEntrySide("CREDIT");
        credit.setEntryAmt(amount);
        credit.setCrtDt(now);

        entryRepository.save(debit);
        entryRepository.save(credit);

        // Credit destination account.
        balance.setLedgerBal(
                balance.getLedgerBal().add(amount)
        );

        balance.setAvailBal(
                balance.getAvailBal().add(amount)
        );

        balance.setUpdDt(now);
        balance.setUpdBy(authentication.getName());

        balanceRepository.save(balance);

        Long payoutId = jdbc.queryForObject(
                """
                INSERT INTO public.bank_fixed_deposit_interest_payout
                (
                    payout_ref,
                    fd_id,
                    dest_acct_id,
                    payout_amt,
                    payout_dt,
                    txn_ref,
                    payout_sts,
                    crt_by
                )
                VALUES (
                    ?, ?, ?, ?, CURRENT_DATE, ?, 'POSTED', ?
                )
                RETURNING payout_id
                """,
                Long.class,
                payoutRef,
                fdId,
                destinationId,
                amount,
                txn.getTxnRef(),
                authentication.getName()
        );

        for (Map<String, Object> row : unpaid) {
            Long accrualId =
                    ((Number) row.get("accrual_id")).longValue();

            jdbc.update(
                    """
                    INSERT INTO public.bank_fixed_deposit_interest_payout_item
                    (payout_id, accrual_id)
                    VALUES (?, ?)
                    """,
                    payoutId,
                    accrualId
            );
        }

        entryRepository.flush();
        balanceRepository.flush();

        return getInternal(payoutId);
    }

    @Transactional(readOnly = true)
    public List<BankFixedDepositInterestPayoutResponse> history(
            Long fdId
    ) {
        return jdbc.query(
                """
                SELECT p.*,
                       (
                           SELECT COUNT(*)
                           FROM public.bank_fixed_deposit_interest_payout_item i
                           WHERE i.payout_id = p.payout_id
                       ) AS accrual_count
                FROM public.bank_fixed_deposit_interest_payout p
                WHERE p.fd_id = ?
                ORDER BY p.payout_id DESC
                """,
                (rs, rowNum) -> mapResponse(rs),
                fdId
        );
    }

    @Transactional(readOnly = true)
    public BigDecimal unpaidInterest(Long fdId) {
        return jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(a.accrued_interest), 0)
                FROM public.bank_fixed_deposit_accrual a
                WHERE a.fd_id = ?
                  AND a.accrual_sts = 'POSTED'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM public.bank_fixed_deposit_interest_payout_item i
                      WHERE i.accrual_id = a.accrual_id
                  )
                """,
                BigDecimal.class,
                fdId
        );
    }

    private BankFixedDepositInterestPayoutResponse getInternal(
            Long payoutId
    ) {
        List<BankFixedDepositInterestPayoutResponse> rows = jdbc.query(
                """
                SELECT p.*,
                       (
                           SELECT COUNT(*)
                           FROM public.bank_fixed_deposit_interest_payout_item i
                           WHERE i.payout_id = p.payout_id
                       ) AS accrual_count
                FROM public.bank_fixed_deposit_interest_payout p
                WHERE p.payout_id = ?
                """,
                (rs, rowNum) -> mapResponse(rs),
                payoutId
        );

        if (rows.isEmpty()) {
            throw notFound("Interest payout not found");
        }

        return rows.get(0);
    }

    private BankFixedDepositInterestPayoutResponse mapResponse(
            ResultSet rs
    ) throws SQLException {
        return new BankFixedDepositInterestPayoutResponse(
                rs.getLong("payout_id"),
                rs.getString("payout_ref"),
                rs.getLong("fd_id"),
                rs.getLong("dest_acct_id"),
                rs.getBigDecimal("payout_amt"),
                rs.getDate("payout_dt").toLocalDate(),
                rs.getString("txn_ref"),
                rs.getString("payout_sts"),
                rs.getInt("accrual_count")
        );
    }

    private BankLedgerAccount findGl(String code) {
        BankLedgerAccount gl = glRepository
                .findByGlCd(code)
                .orElseThrow(() ->
                        conflict("Required GL missing: " + code)
                );

        if (!Boolean.TRUE.equals(gl.getActFlg())
                || !"MYR".equals(gl.getCcyCd())) {
            throw conflict("GL inactive or invalid: " + code);
        }

        return gl;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST, message
        );
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND, message
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, message
        );
    }
}
