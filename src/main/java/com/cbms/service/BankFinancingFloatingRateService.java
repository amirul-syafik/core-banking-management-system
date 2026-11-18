package com.cbms.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Resolves contracted floating terms at approval, independently of later scheme edits. */
@Service
public class BankFinancingFloatingRateService {
    private final JdbcTemplate jdbc;

    public BankFinancingFloatingRateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Terms(String baseRateCode, BigDecimal marginPct, BigDecimal approvalRate) {}

    public Terms termsAtApproval(Long schemeId, LocalDate approvalDate, BigDecimal approvedRate) {
        if (schemeId == null || approvalDate == null || approvedRate == null) {
            throw conflict("Approved floating financing terms are incomplete");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT rate_typ, base_rate_cd, margin_pct
                FROM public.bank_scheme_rate
                WHERE schm_id = ? AND rate_sts = 'ACTIVE' AND eff_dt <= ?
                ORDER BY eff_dt DESC, schm_rate_id DESC LIMIT 1
                """, schemeId, Date.valueOf(approvalDate));
        if (rows.isEmpty() || !"FLOATING".equals(rows.get(0).get("rate_typ"))) {
            throw conflict("Approved financing does not have a floating-rate scheme version");
        }
        String code = (String) rows.get(0).get("base_rate_cd");
        BigDecimal margin = (BigDecimal) rows.get(0).get("margin_pct");
        BigDecimal originalRate = rateOn(code, margin, approvalDate);
        if (originalRate.compareTo(approvedRate) != 0) {
            throw conflict("Approved floating rate does not match the rate at approval; re-approval is required");
        }
        return new Terms(code, margin, originalRate);
    }

    public BigDecimal rateOn(String baseCode, BigDecimal margin, LocalDate asOf) {
        if (baseCode == null || baseCode.isBlank() || margin == null || asOf == null) {
            throw conflict("Floating facility is missing contracted reference rate terms");
        }
        List<BigDecimal> rates = jdbc.queryForList("""
                SELECT rate_pct FROM public.bank_base_rate
                WHERE rate_cd = ? AND ccy_cd = 'MYR' AND rate_sts = 'ACTIVE'
                  AND eff_dt <= ?
                ORDER BY eff_dt DESC, base_rate_id DESC LIMIT 1
                """, BigDecimal.class, baseCode, Date.valueOf(asOf));
        if (rates.isEmpty() || rates.get(0) == null) {
            throw conflict("No effective reference rate for " + baseCode + " on " + asOf);
        }
        BigDecimal effectiveRate = rates.get(0).add(margin);
        if (effectiveRate.signum() < 0 || effectiveRate.compareTo(new BigDecimal("100")) > 0) {
            throw conflict("Floating financing rate is outside allowed 0–100% bounds");
        }
        return effectiveRate;
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
