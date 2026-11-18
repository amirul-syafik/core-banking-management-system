package com.cbms.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/** Resolves an effective-dated MYR reference rate for conventional credit lines. */
@Service
public class BankCreditLineFloatingRateService {
    private final JdbcTemplate jdbc;

    public BankCreditLineFloatingRateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BigDecimal rateOn(String baseCode, BigDecimal contractualMargin, LocalDate date) {
        if (baseCode == null || baseCode.isBlank() || contractualMargin == null || date == null) {
            throw conflict("Floating credit line has no contracted base rate or margin");
        }
        List<BigDecimal> baseRates = jdbc.queryForList(
                """
                        SELECT br.rate_pct
                        FROM public.bank_base_rate br
                        WHERE br.rate_cd = ? AND br.ccy_cd = 'MYR'
                          AND br.rate_sts = 'ACTIVE' AND br.eff_dt <= ?
                        ORDER BY br.eff_dt DESC, br.base_rate_id DESC
                        LIMIT 1
                        """,
                BigDecimal.class, baseCode, Date.valueOf(date));
        if (baseRates.isEmpty() || baseRates.get(0) == null) {
            throw conflict("No effective base rate for floating credit line on " + date);
        }
        try {
            return BankCreditLineFloatingRateCalculator.effectiveRate(baseRates.get(0), contractualMargin);
        } catch (IllegalArgumentException exception) {
            throw conflict(exception.getMessage());
        }
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
