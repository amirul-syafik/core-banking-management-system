package com.cbms.service;

import com.cbms.dto.BankFixedDepositAccrualResponse;
import com.cbms.dto.BankFixedDepositInterestResponse;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BankFixedDepositAccrualService {

    private static final String BASIS = "ACT_365_FIXED";

    private final JdbcTemplate jdbc;
    private final BankFixedDepositInterestService interestService;

    public BankFixedDepositAccrualService(
            JdbcTemplate jdbc,
            BankFixedDepositInterestService interestService
    ) {
        this.jdbc = jdbc;
        this.interestService = interestService;
    }

    @Transactional
    public BankFixedDepositAccrualResponse accrue(
            Long fdId,
            LocalDate accrualDate,
            Authentication authentication
    ) {
        if (accrualDate == null) {
            throw badRequest("Accrual date is required");
        }

        List<Map<String, Object>> deposits = jdbc.queryForList(
                """
                SELECT *
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

        if (!"ACTIVE".equals(fd.get("fd_sts"))) {
            throw conflict("Only ACTIVE deposits can accrue interest");
        }

        LocalDate placementDate =
                ((java.sql.Date) fd.get("placement_dt"))
                        .toLocalDate();

        LocalDate maturityDate =
                ((java.sql.Date) fd.get("maturity_dt"))
                        .toLocalDate();

        LocalDate today = jdbc.queryForObject(
                "SELECT CURRENT_DATE",
                LocalDate.class
        );

        // Accrual date represents the earned period
        // [accrualDate, accrualDate + 1 day].
        if (accrualDate.isBefore(placementDate)
                || !accrualDate.isBefore(maturityDate)) {
            throw conflict("Accrual date is outside deposit tenure");
        }

        if (!accrualDate.isBefore(today)) {
            throw conflict(
                    "Only completed interest days may be accrued"
            );
        }

        Long existing = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.bank_fixed_deposit_accrual
                WHERE fd_id = ?
                  AND accrual_dt = ?
                """,
                Long.class,
                fdId,
                accrualDate
        );

        if (existing != null && existing > 0) {
            throw conflict("Interest already accrued for this date");
        }

        List<Map<String, Object>> previous = jdbc.queryForList(
                """
                SELECT accrual_dt, cumulative_interest
                FROM public.bank_fixed_deposit_accrual
                WHERE fd_id = ?
                ORDER BY accrual_dt DESC
                LIMIT 1
                """,
                fdId
        );

        LocalDate expectedDate = placementDate;
        BigDecimal previousCumulative = BigDecimal.ZERO;

        if (!previous.isEmpty()) {
            expectedDate = ((java.sql.Date)
                    previous.get(0).get("accrual_dt"))
                    .toLocalDate()
                    .plusDays(1);

            previousCumulative = (BigDecimal)
                    previous.get(0).get("cumulative_interest");
        }

        if (!accrualDate.equals(expectedDate)) {
            throw conflict(
                    "Accrual must be sequential. Next date: "
                            + expectedDate
            );
        }

        BigDecimal principal =
                (BigDecimal) fd.get("principal_amt");

        BigDecimal annualRate =
                (BigDecimal) fd.get("interest_rate");

        BankFixedDepositInterestResponse calculation =
                interestService.calculate(
                        principal,
                        annualRate,
                        placementDate,
                        accrualDate.plusDays(1),
                        BASIS
                );

        BigDecimal cumulative = calculation.totalInterest();

        BigDecimal incremental =
                cumulative.subtract(previousCumulative);

        if (incremental.compareTo(BigDecimal.ZERO) < 0) {
            throw conflict("Negative incremental interest");
        }

        String reference = "FD-INT-" + UUID.randomUUID();

        // Resolve the original source account.
        Long sourceAccountId = jdbc.queryForObject(
                """
                SELECT source_acct_id
                FROM public.bank_fixed_deposit_placement
                WHERE fd_id = ?
                """,
                Long.class,
                fdId
        );

        // Zero-cent days still need an accrual record,
        // but must not create a zero-amount transaction.
        if (incremental.compareTo(BigDecimal.ZERO) > 0) {
            postInterest(
                    sourceAccountId,
                    incremental,
                    reference,
                    authentication.getName()
            );
        }

        Long accrualId = jdbc.queryForObject(
                """
                INSERT INTO public.bank_fixed_deposit_accrual
                (
                    fd_id,
                    accrual_dt,
                    day_count_basis,
                    daily_interest,
                    accrued_interest,
                    cumulative_interest,
                    posting_ref,
                    crt_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING accrual_id
                """,
                Long.class,
                fdId,
                accrualDate,
                BASIS,
                calculation.dailyInterest(),
                incremental,
                cumulative,
                reference,
                authentication.getName()
        );

        return getInternal(accrualId);
    }

    private void postInterest(
            Long sourceAccountId,
            BigDecimal amount,
            String reference,
            String username
    ) {
        Long expenseGl = findGl("500100");
        Long payableGl = findGl("200300");

        Long transactionId = jdbc.queryForObject(
                """
                INSERT INTO public.bank_transaction
                (
                    txn_ref,
                    acct_id,
                    txn_typ,
                    txn_amt,
                    ccy_cd,
                    txn_sts,
                    idempotency_key,
                    crt_by
                )
                VALUES (
                    ?, ?, 'FD_INTEREST_ACCRUAL',
                    ?, 'MYR', 'POSTED', ?, ?
                )
                RETURNING txn_id
                """,
                Long.class,
                reference,
                sourceAccountId,
                amount,
                "FD-INT:" + reference,
                username
        );

        jdbc.update(
                """
                INSERT INTO public.bank_ledger_entry
                (
                    txn_id,
                    gl_id,
                    acct_id,
                    entry_side,
                    entry_amt
                )
                VALUES (?, ?, NULL, 'DEBIT', ?)
                """,
                transactionId,
                expenseGl,
                amount
        );

        jdbc.update(
                """
                INSERT INTO public.bank_ledger_entry
                (
                    txn_id,
                    gl_id,
                    acct_id,
                    entry_side,
                    entry_amt
                )
                VALUES (?, ?, NULL, 'CREDIT', ?)
                """,
                transactionId,
                payableGl,
                amount
        );
    }

    private Long findGl(String glCode) {
        List<Long> results = jdbc.queryForList(
                """
                SELECT gl_id
                FROM public.bank_ledger_account
                WHERE gl_cd = ?
                  AND ccy_cd = 'MYR'
                  AND act_flg = TRUE
                """,
                Long.class,
                glCode
        );

        if (results.size() != 1) {
            throw conflict(
                    "Expected exactly one active GL: " + glCode
            );
        }

        return results.get(0);
    }

    @Transactional(readOnly = true)
    public List<BankFixedDepositAccrualResponse> history(
            Long fdId
    ) {
        return jdbc.query(
                """
                SELECT *
                FROM public.bank_fixed_deposit_accrual
                WHERE fd_id = ?
                ORDER BY accrual_dt
                """,
                (rs, rowNum) -> mapResponse(rs),
                fdId
        );
    }

    @Transactional(readOnly = true)
    public BigDecimal totalAccrued(Long fdId) {
        return jdbc.queryForObject(
                """
                SELECT COALESCE(
                    SUM(accrued_interest), 0
                )
                FROM public.bank_fixed_deposit_accrual
                WHERE fd_id = ?
                """,
                BigDecimal.class,
                fdId
        );
    }

    private BankFixedDepositAccrualResponse getInternal(
            Long accrualId
    ) {
        List<BankFixedDepositAccrualResponse> rows = jdbc.query(
                """
                SELECT *
                FROM public.bank_fixed_deposit_accrual
                WHERE accrual_id = ?
                """,
                (rs, rowNum) -> mapResponse(rs),
                accrualId
        );

        if (rows.isEmpty()) {
            throw notFound("Accrual not found");
        }

        return rows.get(0);
    }

    private BankFixedDepositAccrualResponse mapResponse(
            ResultSet rs
    ) throws SQLException {
        return new BankFixedDepositAccrualResponse(
                rs.getLong("accrual_id"),
                rs.getLong("fd_id"),
                rs.getDate("accrual_dt").toLocalDate(),
                rs.getString("day_count_basis"),
                rs.getBigDecimal("daily_interest"),
                rs.getBigDecimal("accrued_interest"),
                rs.getBigDecimal("cumulative_interest"),
                rs.getString("posting_ref"),
                rs.getString("accrual_sts")
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                message
        );
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}
