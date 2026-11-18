package com.cbms.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Latest effective scheme rate, with floating prices resolved from base + margin. */
final class BankFinancingPricingPolicy {
    private BankFinancingPricingPolicy() {}

    record RatePolicy(String rateType, BigDecimal defaultRate, BigDecimal minimum, BigDecimal maximum) {}

    static RatePolicy resolve(JdbcTemplate jdbc, Long schmId, LocalDate asOf) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                    SELECT rate_typ, fixed_rate_pct, min_rate_pct, max_rate_pct, base_rate_cd, margin_pct
                    FROM public.bank_scheme_rate
                    WHERE schm_id = ? AND rate_sts = 'ACTIVE' AND eff_dt <= ?
                    ORDER BY eff_dt DESC, schm_rate_id DESC LIMIT 1
                """, schmId, asOf);
        if (rows.isEmpty()) throw conflict("No effective scheme rate is configured");
        Map<String, Object> rate = rows.get(0);
        String type = (String) rate.get("rate_typ");
        if ("FIXED".equals(type)) {
            BigDecimal standard = (BigDecimal) rate.get("fixed_rate_pct");
            if (standard == null) throw conflict("Fixed scheme rate is not configured");
            BigDecimal min = rate.get("min_rate_pct") == null ? standard : (BigDecimal) rate.get("min_rate_pct");
            BigDecimal max = rate.get("max_rate_pct") == null ? standard : (BigDecimal) rate.get("max_rate_pct");
            return new RatePolicy(type, standard, min, max);
        }
        if ("FLOATING".equals(type)) {
            String code = (String) rate.get("base_rate_cd");
            BigDecimal margin = (BigDecimal) rate.get("margin_pct");
            if (code == null || code.isBlank() || margin == null) throw conflict("Floating rate configuration is incomplete");
            List<Map<String, Object>> baseRates = jdbc.queryForList(
                    """
                        SELECT rate_pct, ccy_cd FROM public.bank_base_rate
                        WHERE rate_cd = ? AND rate_sts = 'ACTIVE' AND eff_dt <= ?
                        ORDER BY eff_dt DESC, base_rate_id DESC LIMIT 1
                    """, code, asOf);
            if (baseRates.isEmpty() || baseRates.get(0).get("rate_pct") == null)
                throw conflict("No applicable floating base rate is configured");
            if (!"MYR".equals(baseRates.get(0).get("ccy_cd")))
                throw conflict("Floating base rate currency must be MYR");
            BigDecimal effective = ((BigDecimal) baseRates.get(0).get("rate_pct")).add(margin);
            return new RatePolicy(type, effective, effective, effective);
        }
        throw conflict("Unsupported scheme rate type");
    }

    static void validateSelected(RatePolicy policy, BigDecimal selected) {
        if (selected == null || selected.compareTo(BigDecimal.ZERO) < 0 || selected.compareTo(new BigDecimal("100")) > 0
                || selected.compareTo(policy.minimum()) < 0 || selected.compareTo(policy.maximum()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Selected rate is outside the current effective scheme policy");
        }
    }

    static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
