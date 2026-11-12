package com.cbms.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BankFinancingPricingPolicyTest {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 12);

    @Test
    void fixedRateRespectsBounds() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> row = new HashMap<>();
        row.put("rate_typ", "FIXED");
        row.put("fixed_rate_pct", new BigDecimal("6.50"));
        row.put("min_rate_pct", new BigDecimal("6.00"));
        row.put("max_rate_pct", new BigDecimal("7.00"));
        when(jdbc.queryForList(anyString(), eq(12L), eq(DATE))).thenReturn(List.of(row));

        var policy = BankFinancingPricingPolicy.resolve(jdbc, 12L, DATE);
        assertEquals("FIXED", policy.rateType());
        assertDoesNotThrow(() -> BankFinancingPricingPolicy.validateSelected(policy, new BigDecimal("6.25")));
        assertThrows(ResponseStatusException.class,
                () -> BankFinancingPricingPolicy.validateSelected(policy, new BigDecimal("7.50")));
    }

    @Test
    void floatingRateUsesEffectiveBasePlusMarginAndRejectsArbitraryRate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> row = new HashMap<>();
        row.put("rate_typ", "FLOATING");
        row.put("base_rate_cd", "BR");
        row.put("margin_pct", new BigDecimal("1.25"));
        when(jdbc.queryForList(anyString(), eq(12L), eq(DATE))).thenReturn(List.of(row));
        Map<String, Object> base = Map.of("rate_pct", new BigDecimal("3.00"), "ccy_cd", "MYR");
        when(jdbc.queryForList(anyString(), eq("BR"), eq(DATE))).thenReturn(List.of(base));

        var policy = BankFinancingPricingPolicy.resolve(jdbc, 12L, DATE);
        assertEquals("FLOATING", policy.rateType());
        assertEquals(0, policy.defaultRate().compareTo(new BigDecimal("4.25")));
        assertDoesNotThrow(() -> BankFinancingPricingPolicy.validateSelected(policy, new BigDecimal("4.25")));
        assertThrows(ResponseStatusException.class,
                () -> BankFinancingPricingPolicy.validateSelected(policy, new BigDecimal("4.30")));
    }

    @Test
    void floatingRateRejectsMismatchedBaseCurrency() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> row = new HashMap<>();
        row.put("rate_typ", "FLOATING");
        row.put("base_rate_cd", "BR");
        row.put("margin_pct", new BigDecimal("1.25"));
        when(jdbc.queryForList(anyString(), eq(12L), eq(DATE))).thenReturn(List.of(row));
        when(jdbc.queryForList(anyString(), eq("BR"), eq(DATE))).thenReturn(
                List.of(Map.of("rate_pct", new BigDecimal("3.00"), "ccy_cd", "USD")));
        assertThrows(ResponseStatusException.class,
                () -> BankFinancingPricingPolicy.resolve(jdbc, 12L, DATE));
    }
}
