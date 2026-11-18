package com.cbms.service;

import com.cbms.dto.BankFinancingRepriceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Advances a conventional floating facility to the latest contracted reference rate.
 * Only future, completely unpaid instalments change. Paid, overdue and due-today
 * instalments retain their original scheduled values and audit/ledger entries.
 */
@Service
public class BankFinancingFloatingRepricingService {
    private final JdbcTemplate jdbc;
    private final BankFinancingFloatingRateService rates;

    public BankFinancingFloatingRepricingService(JdbcTemplate jdbc, BankFinancingFloatingRateService rates) {
        this.jdbc = jdbc;
        this.rates = rates;
    }

    @Transactional
    public BankFinancingRepriceResponse reprice(Long facId, LocalDate asOf, String actor) {
        if (facId == null || asOf == null || actor == null || actor.isBlank()
                || !asOf.equals(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repricing must use the current business date");
        }
        List<Map<String, Object>> facilities = jdbc.queryForList("""
                SELECT f.fac_id, f.rate_typ, f.fac_typ, f.ccy_cd, f.fac_sts,
                       f.base_rate_cd, f.margin_pct, f.eff_rate, p.banking_typ
                FROM public.bank_financing_facility f
                JOIN public.bank_product p ON p.prod_id = f.prod_id
                WHERE f.fac_id = ? FOR UPDATE OF f
                """, facId);
        if (facilities.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Financing facility not found");
        }
        Map<String, Object> facility = facilities.get(0);
        if (!"FLOATING".equals(facility.get("rate_typ"))
                || !"HIRE_PURCHASE".equals(facility.get("fac_typ"))
                || !"CONVENTIONAL".equals(facility.get("banking_typ"))
                || !"MYR".equals(facility.get("ccy_cd"))) {
            throw conflict("Repricing is only available for conventional floating-rate hire purchase");
        }
        if (!"DRAFT".equals(facility.get("fac_sts"))
                && !"ACTIVE".equals(facility.get("fac_sts"))) {
            throw conflict("Facility is not eligible for repricing");
        }
        BigDecimal oldRate = (BigDecimal) facility.get("eff_rate");
        BigDecimal currentRate = rates.rateOn((String) facility.get("base_rate_cd"),
                (BigDecimal) facility.get("margin_pct"), asOf);
        if (oldRate == null) throw conflict("Facility has no existing effective rate");

        // Hold schedule locks through the journal and all updates.
        List<Map<String, Object>> future = jdbc.queryForList("""
                SELECT sched_id, opening_principal, principal_amt, interest_amt,
                       paid_interest, paid_principal, inst_sts
                FROM public.bank_financing_repayment_schedule
                WHERE fac_id = ? AND due_dt > ?
                ORDER BY inst_no FOR UPDATE
                """, facId, Date.valueOf(asOf));
        if (currentRate.compareTo(oldRate) == 0) {
            // A rate review is still recorded on the facility even when there is
            // no pricing change. Do not create a duplicate financial reprice audit.
            jdbc.update("""
                    UPDATE public.bank_financing_facility
                    SET last_reprice_dt = ?, upd_by = ?, upd_dt = CURRENT_TIMESTAMP
                    WHERE fac_id = ?
                    """, Date.valueOf(asOf), actor, facId);
            BigDecimal futureInterest = sumInterest(future);
            return new BankFinancingRepriceResponse(facId, asOf, oldRate, currentRate, 0,
                    futureInterest, futureInterest);
        }
        for (Map<String, Object> row : future) {
            if (((BigDecimal) row.get("paid_interest")).signum() != 0
                    || ((BigDecimal) row.get("paid_principal")).signum() != 0
                    || "PAID".equals(row.get("inst_sts"))) {
                throw conflict("A future instalment is already paid or partially paid; manual review is required");
            }
        }
        BigDecimal before = BigDecimal.ZERO.setScale(2);
        BigDecimal after = BigDecimal.ZERO.setScale(2);
        for (Map<String, Object> row : future) {
            BigDecimal original = (BigDecimal) row.get("interest_amt");
            BigDecimal updated = BankFinancingFloatingRateCalculator.monthlyInterest(
                    (BigDecimal) row.get("opening_principal"), currentRate);
            before = before.add(original);
            after = after.add(updated);
            jdbc.update("""
                    UPDATE public.bank_financing_repayment_schedule
                    SET interest_amt = ?, inst_amt = principal_amt + ?, applied_rate_pct = ?
                    WHERE sched_id = ?
                    """, updated, updated, currentRate, row.get("sched_id"));
        }
        jdbc.update("""
                UPDATE public.bank_financing_facility
                SET eff_rate = ?, last_reprice_dt = ?, upd_by = ?, upd_dt = CURRENT_TIMESTAMP
                WHERE fac_id = ?
                """, currentRate, Date.valueOf(asOf), actor, facId);
        jdbc.update("""
                INSERT INTO public.bank_financing_rate_repricing
                (fac_id, review_dt, old_rate_pct, new_rate_pct, updated_instalments,
                 previous_interest, revised_interest, action_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, facId, Date.valueOf(asOf), oldRate, currentRate, future.size(), before, after, actor);
        return new BankFinancingRepriceResponse(facId, asOf, oldRate, currentRate,
                future.size(), before, after);
    }

    private static BigDecimal sumInterest(List<Map<String, Object>> rows) {
        BigDecimal sum = BigDecimal.ZERO.setScale(2);
        for (Map<String, Object> row : rows) sum = sum.add((BigDecimal) row.get("interest_amt"));
        return sum;
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
