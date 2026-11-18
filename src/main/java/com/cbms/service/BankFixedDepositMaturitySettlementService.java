package com.cbms.service;

import com.cbms.reference.BusinessReferenceGenerator;

import com.cbms.dto.BankFixedDepositMaturitySettlementRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit principal-only settlement after maturity. Interest must be paid
 * through the existing interest payout workflow before the FD can close.
 * The FD row lock serializes settlement and renewal attempts for the same FD.
 */
@Service
public class BankFixedDepositMaturitySettlementService {
    private final JdbcTemplate jdbc;

    public BankFixedDepositMaturitySettlementService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> settle(Long fdId,
                                      BankFixedDepositMaturitySettlementRequest request,
                                      String actor) {
        if (fdId == null || fdId <= 0 || request == null
                || request.destinationAccountId() == null || request.destinationAccountId() <= 0) {
            throw error(HttpStatus.BAD_REQUEST, "Valid FD and destination account are required");
        }
        if (actor == null || actor.isBlank()) {
            throw error(HttpStatus.UNAUTHORIZED, "Authenticated user is required");
        }
        List<Map<String, Object>> deposits = jdbc.queryForList("""
                SELECT fd_id, fd_ref, cust_id, principal_amt, ccy_cd, fd_sts
                FROM public.bank_fixed_deposit WHERE fd_id = ? FOR UPDATE
                """, fdId);
        if (deposits.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Fixed deposit not found");
        Map<String, Object> fd = deposits.get(0);
        BankIslamicDepositGuard.requireConventional(jdbc, fdId, "maturity settlement");

        if (!"MATURED".equals(fd.get("fd_sts")))
            throw error(HttpStatus.CONFLICT, "Only an unsettled MATURED deposit can be settled");
        if (!"MYR".equals(fd.get("ccy_cd")))
            throw error(HttpStatus.CONFLICT, "Only MYR settlement is supported");
        BigDecimal principal = (BigDecimal) fd.get("principal_amt");
        if (principal == null || principal.signum() <= 0)
            throw error(HttpStatus.CONFLICT, "Invalid FD principal");
        int maturityCount = count("""
                SELECT COUNT(*) FROM public.bank_fixed_deposit_maturity
                WHERE fd_id = ? AND maturity_sts = 'PROCESSED' AND principal_amt = ?
                """, fdId, principal);
        if (maturityCount != 1)
            throw error(HttpStatus.CONFLICT, "Processed maturity record is not verified");
        if (count("SELECT COUNT(*) FROM public.bank_fixed_deposit_renewal WHERE original_fd_id = ?", fdId) != 0)
            throw error(HttpStatus.CONFLICT, "A renewed deposit cannot also be repaid");
        String idemKey = "FD-MATURITY-SETTLEMENT:" + fdId;
        if (count("SELECT COUNT(*) FROM public.bank_transaction WHERE idempotency_key = ?", idemKey) != 0)
            throw error(HttpStatus.CONFLICT, "Principal was already settled");

        // Fully accrue and pay interest before closing; an FD CLOSED by this
        // settlement cannot enter the existing interest payout workflow.
        BigDecimal accrued = jdbc.queryForObject("""
                SELECT COALESCE(SUM(accrued_interest), 0)
                FROM public.bank_fixed_deposit_accrual
                WHERE fd_id = ? AND accrual_sts = 'POSTED'
                """, BigDecimal.class, fdId);
        BigDecimal paid = jdbc.queryForObject("""
                SELECT COALESCE(SUM(payout_amt), 0)
                FROM public.bank_fixed_deposit_interest_payout
                WHERE fd_id = ? AND payout_sts = 'POSTED'
                """, BigDecimal.class, fdId);
        if (accrued == null || paid == null || accrued.compareTo(paid) != 0)
            throw error(HttpStatus.CONFLICT, "Pay all outstanding accrued interest before principal settlement");

        Long customerId = ((Number) fd.get("cust_id")).longValue();
        if (count("SELECT COUNT(*) FROM public.customer_master WHERE cust_id = ? AND sts = 'ACTIVE'", customerId) != 1)
            throw error(HttpStatus.CONFLICT, "Customer must be ACTIVE");
        Long destId = request.destinationAccountId();
        List<Map<String, Object>> accounts = jdbc.queryForList("""
                SELECT acct_id, cust_id, acct_sts, acct_typ, ccy_cd
                FROM public.bank_account WHERE acct_id = ? FOR UPDATE
                """, destId);
        if (accounts.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Destination account not found");
        Map<String, Object> dest = accounts.get(0);
        if (((Number) dest.get("cust_id")).longValue() != customerId
                || !"ACTIVE".equals(dest.get("acct_sts"))
                || !"MYR".equals(dest.get("ccy_cd"))
                || !("SAVINGS".equals(dest.get("acct_typ")) || "CURRENT".equals(dest.get("acct_typ"))))
            throw error(HttpStatus.CONFLICT, "Destination must be the customer's active MYR savings/current account");
        if (jdbc.queryForList("SELECT acct_id FROM public.bank_account_balance WHERE acct_id = ? FOR UPDATE", destId).size() != 1)
            throw error(HttpStatus.CONFLICT, "Destination balance is unavailable");

        Long fdGl = gl("200200");
        Long depositGl = gl("200100");
        String txRef = BusinessReferenceGenerator.next(jdbc, BusinessReferenceGenerator.Stream.TRANSACTION);
        Long txId = jdbc.queryForObject("""
                INSERT INTO public.bank_transaction
                  (txn_ref, acct_id, txn_typ, txn_amt, ccy_cd, txn_sts, idempotency_key, crt_by)
                VALUES (?, ?, 'FD_MATURITY_SETTLEMENT', ?, 'MYR', 'POSTED', ?, ?)
                RETURNING txn_id
                """, Long.class, txRef, destId, principal, idemKey, actor);
        jdbc.update("""
                INSERT INTO public.bank_ledger_entry (txn_id, gl_id, acct_id, entry_side, entry_amt)
                VALUES (?, ?, NULL, 'DEBIT', ?)
                """, txId, fdGl, principal);
        jdbc.update("""
                INSERT INTO public.bank_ledger_entry (txn_id, gl_id, acct_id, entry_side, entry_amt)
                VALUES (?, ?, ?, 'CREDIT', ?)
                """, txId, depositGl, destId, principal);
        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT COALESCE(SUM(entry_amt) FILTER (WHERE entry_side = 'DEBIT'),0) AS debit,
                       COALESCE(SUM(entry_amt) FILTER (WHERE entry_side = 'CREDIT'),0) AS credit,
                       COUNT(*) AS entries
                FROM public.bank_ledger_entry WHERE txn_id = ?
                """, txId);
        if (((Number) totals.get("entries")).intValue() != 2
                || ((BigDecimal) totals.get("debit")).compareTo(principal) != 0
                || ((BigDecimal) totals.get("credit")).compareTo(principal) != 0)
            throw error(HttpStatus.CONFLICT, "Maturity settlement GL entries are invalid");
        if (jdbc.update("""
                UPDATE public.bank_account_balance
                SET ledger_bal = ledger_bal + ?, avail_bal = avail_bal + ?,
                    upd_dt = CURRENT_TIMESTAMP, upd_by = ? WHERE acct_id = ?
                """, principal, principal, actor, destId) != 1)
            throw error(HttpStatus.CONFLICT, "Unable to credit destination account");
        if (jdbc.update("""
                UPDATE public.bank_fixed_deposit
                SET fd_sts = 'CLOSED', upd_dt = CURRENT_TIMESTAMP, upd_by = ?
                WHERE fd_id = ? AND fd_sts = 'MATURED'
                """, actor, fdId) != 1)
            throw error(HttpStatus.CONFLICT, "Unable to close matured deposit");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("fdId", fdId);
        response.put("fdReference", fd.get("fd_ref"));
        response.put("destinationAccountId", destId);
        response.put("principalAmount", principal);
        response.put("settlementAmount", principal);
        response.put("transactionId", txId);
        response.put("transactionReference", txRef);
        response.put("settlementDate", LocalDate.now());
        response.put("status", "CLOSED");
        response.put("principalRepaid", true);
        return response;
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private Long gl(String code) {
        List<Long> ids = jdbc.queryForList("""
                SELECT gl_id FROM public.bank_ledger_account
                WHERE gl_cd = ? AND ccy_cd = 'MYR' AND act_flg = TRUE
                """, Long.class, code);
        if (ids.size() != 1) throw error(HttpStatus.CONFLICT, "Required active GL unavailable: " + code);
        return ids.get(0);
    }

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
