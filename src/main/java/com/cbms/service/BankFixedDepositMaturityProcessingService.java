
package com.cbms.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BankFixedDepositMaturityProcessingService {

    private final JdbcTemplate jdbc;

    public BankFixedDepositMaturityProcessingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> process(Long fdId, String username) {

        if (fdId == null || fdId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid fixed deposit ID"
            );
        }

        // Lock FD to prevent concurrent maturity processing.
        List<Map<String, Object>> deposits = jdbc.queryForList(
                """
                SELECT fd_id, fd_ref, cust_id, principal_amt,
                       placement_dt, maturity_dt, fd_sts, ccy_cd
                FROM public.bank_fixed_deposit
                WHERE fd_id = ?
                FOR UPDATE
                """,
                fdId
        );

        if (deposits.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Fixed deposit not found"
            );
        }

        Map<String, Object> fd = deposits.get(0);

        if (!"ACTIVE".equals(fd.get("fd_sts"))) {
            throw conflict("Only ACTIVE fixed deposits can mature");
        }

        LocalDate placementDate =
                ((java.sql.Date) fd.get("placement_dt")).toLocalDate();

        LocalDate maturityDate =
                ((java.sql.Date) fd.get("maturity_dt")).toLocalDate();

        if (LocalDate.now().isBefore(maturityDate)) {
            throw conflict("Fixed deposit has not reached maturity date");
        }

        BigDecimal principal =
                (BigDecimal) fd.get("principal_amt");

        // Verify ordinary FD placement or internally funded renewal.
        Integer fundedCount = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.bank_fixed_deposit_placement p
                JOIN public.bank_transaction t
                  ON t.txn_ref = p.posting_ref
                WHERE p.fd_id = ?
                  AND p.placement_sts = 'POSTED'
                  AND p.placement_amt = ?
                  AND p.ccy_cd = ?
                  AND p.funding_typ IN (
                      'FD_PLACEMENT', 'FD_RENEWAL'
                  )
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
                            AND m.principal_amt = r.principal_amt
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
                Integer.class,
                fdId,
                principal,
                fd.get("ccy_cd")
        );

        if (fundedCount == null || fundedCount != 1) {
            throw conflict("Fixed deposit funding is not verified");
        }

        // Every contractual interest day must have an accrual record.
        // The maturity date itself is excluded.
        Long missingDays = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM generate_series(
                    ?::date,
                    (?::date - INTERVAL '1 day')::date,
                    INTERVAL '1 day'
                ) AS d(accrual_day)
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM public.bank_fixed_deposit_accrual a
                    WHERE a.fd_id = ?
                      AND a.accrual_dt = d.accrual_day::date
                      AND a.accrual_sts = 'POSTED'
                )
                """,
                Long.class,
                placementDate,
                maturityDate,
                fdId
        );

        if (missingDays != null && missingDays > 0) {
            throw conflict(
                    "Missing interest accruals for "
                            + missingDays + " day(s)"
            );
        }

        Integer existing = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.bank_fixed_deposit_maturity
                WHERE fd_id = ?
                """,
                Integer.class,
                fdId
        );

        if (existing != null && existing > 0) {
            throw conflict("Fixed deposit maturity already processed");
        }

        String maturityRef = "FD-MAT-" + UUID.randomUUID();

        jdbc.update(
                """
                INSERT INTO public.bank_fixed_deposit_maturity
                (
                    fd_id, maturity_ref, maturity_dt,
                    principal_amt, maturity_sts,
                    remarks, crt_by
                )
                VALUES (?, ?, ?, ?, 'PROCESSED', ?, ?)
                """,
                fdId,
                maturityRef,
                maturityDate,
                principal,
                "Maturity processing; principal not yet repaid",
                username
        );

        int updated = jdbc.update(
                """
                UPDATE public.bank_fixed_deposit
                SET fd_sts = 'MATURED',
                    upd_dt = CURRENT_TIMESTAMP,
                    upd_by = ?
                WHERE fd_id = ?
                  AND fd_sts = 'ACTIVE'
                """,
                username,
                fdId
        );

        if (updated != 1) {
            throw conflict("Unable to update fixed deposit status");
        }

        return Map.of(
                "fdId", fdId,
                "fdReference", fd.get("fd_ref"),
                "maturityReference", maturityRef,
                "maturityDate", maturityDate.toString(),
                "principalAmount", principal,
                "status", "MATURED",
                "principalRepaid", false
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}
