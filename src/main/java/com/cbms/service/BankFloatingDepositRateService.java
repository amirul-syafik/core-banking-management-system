package com.cbms.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/** Resolves effective-dated base rates; stores no mutable pricing state. */
@Service
public class BankFloatingDepositRateService {
    private final JdbcTemplate jdbc;

    public BankFloatingDepositRateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BigDecimal rateOn(String baseCode, BigDecimal margin, LocalDate date) {
        var history = history(baseCode, date.plusDays(1));
        try {
            var result = BankFloatingDepositRateCalculator.calculate(
                    BigDecimal.ONE, margin, BigDecimal.ZERO, date, date.plusDays(1), history);
            return result.currentRatePct();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    public BankFloatingDepositRateCalculator.Result calculate(
            BigDecimal principal, String baseCode, BigDecimal margin,
            BigDecimal penalty, LocalDate start, LocalDate endExclusive) {
        try {
            return BankFloatingDepositRateCalculator.calculate(principal, margin, penalty,
                    start, endExclusive, history(baseCode, endExclusive));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    private List<BankFloatingDepositRateCalculator.RateVersion> history(
            String code, LocalDate endExclusive) {
        if (code == null || code.isBlank() || endExclusive == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Floating deposit has no base-rate reference");
        }
        return jdbc.query("""
                        SELECT eff_dt, rate_pct
                        FROM public.bank_base_rate
                        WHERE rate_cd = ? AND rate_sts = 'ACTIVE' AND ccy_cd = 'MYR'
                          AND eff_dt < ?
                        ORDER BY eff_dt ASC, base_rate_id ASC
                        """,
                (rs, row) -> new BankFloatingDepositRateCalculator.RateVersion(
                        rs.getDate("eff_dt").toLocalDate(), rs.getBigDecimal("rate_pct")),
                code, Date.valueOf(endExclusive));
    }
}
