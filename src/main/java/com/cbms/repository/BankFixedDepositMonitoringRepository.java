package com.cbms.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.sql.Date;
import java.time.LocalDate;

@Repository
public class BankFixedDepositMonitoringRepository {

    private final JdbcTemplate jdbc;

    public BankFixedDepositMonitoringRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> getSummary() {

        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT
                    CURRENT_DATE AS business_date,
                
                    COUNT(*) AS total_fd,
                
                    COALESCE(SUM(principal_amt), 0)
                        AS total_principal,
                
                    COUNT(*) FILTER (
                        WHERE fd_sts = 'ACTIVE'
                    ) AS active_count,
                
                    COALESCE(SUM(principal_amt) FILTER (
                        WHERE fd_sts = 'ACTIVE'
                    ), 0) AS active_principal,
                
                    COUNT(*) FILTER (
                        WHERE fd_sts = 'DRAFT'
                    ) AS draft_count,
                
                    COUNT(*) FILTER (
                        WHERE fd_sts = 'MATURED'
                    ) AS matured_count,
                
                    COUNT(*) FILTER (
                        WHERE fd_sts = 'CLOSED'
                    ) AS closed_count,
                
                    COUNT(*) FILTER (
                        WHERE fd_sts = 'CANCELLED'
                    ) AS cancelled_count,
                
                    COUNT(*) FILTER (
                        WHERE fd_sts = 'ACTIVE'
                          AND maturity_dt >= CURRENT_DATE
                          AND maturity_dt <= CURRENT_DATE + 30
                    ) AS upcoming_maturity_count,
                
                    COUNT(*) FILTER (
                        WHERE fd_sts = 'ACTIVE'
                          AND maturity_dt < CURRENT_DATE
                    ) AS overdue_maturity_count
                
                FROM public.bank_fixed_deposit
                """);

        Map<String, Object> withdrawals = jdbc.queryForMap("""
                SELECT
                    COUNT(*) AS total_withdrawals,
                
                    COALESCE(SUM(principal_amt), 0)
                        AS total_withdrawn_principal,
                
                    COALESCE(SUM(settlement_amt), 0)
                        AS total_withdrawal_settlement,
                
                    COALESCE(SUM(eligible_interest), 0)
                        AS total_eligible_interest
                
                FROM public.bank_fd_premature_withdrawal
                
                WHERE withdrawal_sts = 'POSTED'
                """);

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("businessDate", totals.remove("business_date"));
        result.put("fixedDeposits", totals);
        result.put("prematureWithdrawals", withdrawals);

        return formatDates(result);
    }

    public List<Map<String, Object>> getUpcomingMaturities(int days) {

        return formatDates(jdbc.queryForList("""
                SELECT
                    fd_id,
                    fd_ref,
                    cust_id,
                    prod_id,
                    schm_id,
                    ccy_cd,
                    principal_amt,
                    interest_rate,
                    placement_dt,
                    maturity_dt,
                
                    maturity_dt - CURRENT_DATE
                        AS days_remaining,
                
                    fd_sts
                
                FROM public.bank_fixed_deposit
                
                WHERE fd_sts = 'ACTIVE'
                  AND maturity_dt >= CURRENT_DATE
                  AND maturity_dt <= CURRENT_DATE + ?
                
                ORDER BY maturity_dt, fd_id
                """, days));
    }

    public List<Map<String, Object>> getOverdueMaturities() {

        return formatDates(jdbc.queryForList("""
                SELECT
                    fd_id,
                    fd_ref,
                    cust_id,
                    prod_id,
                    schm_id,
                    ccy_cd,
                    principal_amt,
                    placement_dt,
                    maturity_dt,
                
                    CURRENT_DATE - maturity_dt
                        AS days_overdue,
                
                    fd_sts
                
                FROM public.bank_fixed_deposit
                
                WHERE fd_sts = 'ACTIVE'
                  AND maturity_dt < CURRENT_DATE
                
                ORDER BY maturity_dt, fd_id
                """));
    }

    public List<Map<String, Object>> getMissingAccruals() {

        return formatDates(jdbc.queryForList("""
                WITH expected_dates AS (
                
                    SELECT
                        fd.fd_id,
                        fd.fd_ref,
                        fd.cust_id,
                        fd.principal_amt,
                        fd.placement_dt,
                        fd.maturity_dt,
                
                        d.accrual_date::date
                            AS expected_accrual_dt
                
                    FROM public.bank_fixed_deposit fd
                
                    CROSS JOIN LATERAL generate_series(
                        fd.placement_dt,
                        LEAST(
                            CURRENT_DATE - 1,
                            fd.maturity_dt - 1
                        ),
                        INTERVAL '1 day'
                    ) AS d(accrual_date)
                
                    WHERE fd.fd_sts = 'ACTIVE'
                      AND fd.placement_dt < CURRENT_DATE
                      AND fd.maturity_dt IS NOT NULL
                )
                
                SELECT
                    e.fd_id,
                    e.fd_ref,
                    e.cust_id,
                    e.principal_amt,
                    e.placement_dt,
                    e.maturity_dt,
                
                    COUNT(*) AS missing_days,
                
                    MIN(e.expected_accrual_dt)
                        AS first_missing_date,
                
                    MAX(e.expected_accrual_dt)
                        AS last_missing_date
                
                FROM expected_dates e
                
                WHERE NOT EXISTS (
                    SELECT 1
                
                    FROM public.bank_fixed_deposit_accrual a
                
                    WHERE a.fd_id = e.fd_id
                      AND a.accrual_dt = e.expected_accrual_dt
                      AND a.accrual_sts = 'POSTED'
                )
                
                GROUP BY
                    e.fd_id,
                    e.fd_ref,
                    e.cust_id,
                    e.principal_amt,
                    e.placement_dt,
                    e.maturity_dt
                
                ORDER BY missing_days DESC, e.fd_id
                """));
    }

    public List<Map<String, Object>> getClosureHistory() {

        return formatDates(jdbc.queryForList("""
                SELECT
                    fd.fd_id,
                    fd.fd_ref,
                    fd.cust_id,
                    fd.ccy_cd,
                    fd.principal_amt,
                    fd.placement_dt,
                    fd.maturity_dt,
                    fd.fd_sts,
                    fd.upd_dt AS last_updated_date,
                
                    CASE
                        WHEN pw.withdrawal_id IS NOT NULL
                            THEN 'PREMATURE_WITHDRAWAL'
                        ELSE 'OTHER_CLOSURE'
                    END AS closure_type,
                
                    pw.withdrawal_id,
                    pw.withdrawal_ref,
                    pw.withdrawal_dt,
                    pw.holding_days,
                    pw.contracted_rate,
                    pw.recalculated_rate,
                    pw.penalty_rate,
                    pw.effective_rate,
                    pw.eligible_interest,
                    pw.accrued_interest,
                    pw.previously_paid_interest,
                    pw.interest_adjustment,
                    pw.interest_to_pay,
                    pw.interest_recovery,
                    pw.settlement_amt,
                    pw.withdrawal_sts,
                    pw.posting_ref
                
                FROM public.bank_fixed_deposit fd
                
                LEFT JOIN LATERAL (
                    SELECT p.*
                
                    FROM public.bank_fd_premature_withdrawal p
                
                    WHERE p.fd_id = fd.fd_id
                      AND p.withdrawal_sts = 'POSTED'
                
                    ORDER BY p.withdrawal_id DESC
                
                    LIMIT 1
                ) pw ON TRUE
                
                WHERE fd.fd_sts = 'CLOSED'
                
                ORDER BY fd.upd_dt DESC, fd.fd_id DESC
                """));
    }

    private Map<String, Object> formatDates(Map<String, Object> row) {

        Map<String, Object> result = new LinkedHashMap<>(row);

        result.replaceAll((key, value) -> {
            if (value instanceof Date date) {
                return date.toLocalDate();
            }
            return value;
        });

        return result;
    }

    private List<Map<String, Object>> formatDates(
            List<Map<String, Object>> rows
    ) {
        return rows.stream()
                .map(this::formatDates)
                .toList();
    }
}
