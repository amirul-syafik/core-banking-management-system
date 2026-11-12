package com.cbms.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class BankFinancingFloatingRateCalculatorTest {
    @Test
    void monthlyInterestUsesRemainingPrincipal() {
        assertEquals(new BigDecimal("50.00"), BankFinancingFloatingRateCalculator.monthlyInterest(
                new BigDecimal("10000"), new BigDecimal("6.00")));
        assertEquals(new BigDecimal("25.00"), BankFinancingFloatingRateCalculator.monthlyInterest(
                new BigDecimal("5000"), new BigDecimal("6.00")));
    }

    @Test
    void repricingRecalculatesFutureInterestNotPrincipal() {
        BigDecimal principal = new BigDecimal("10000");
        BigDecimal oldRate = new BigDecimal("5");
        BigDecimal newRate = new BigDecimal("7");
        assertEquals(new BigDecimal("41.67"), BankFinancingFloatingRateCalculator.monthlyInterest(principal, oldRate));
        assertEquals(new BigDecimal("58.33"), BankFinancingFloatingRateCalculator.monthlyInterest(principal, newRate));
    }

    @Test
    void principalRemainderReconcilesExactly() {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 1; i <= 12; i++) {
            sum = sum.add(BankFinancingFloatingRateCalculator.principalInstalment(
                    new BigDecimal("10000.00"), 12, i));
        }
        assertEquals(0, sum.compareTo(new BigDecimal("10000.00")));
        // Ensure the final instalment is never negative for small principals.
        assertEquals(new BigDecimal("0.00"), BankFinancingFloatingRateCalculator.principalInstalment(
                new BigDecimal("0.02"), 4, 1));
        assertEquals(new BigDecimal("0.02"), BankFinancingFloatingRateCalculator.principalInstalment(
                new BigDecimal("0.02"), 4, 4));
    }

    @Test
    void invalidRatesAndAmountsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BankFinancingFloatingRateCalculator.monthlyInterest(
                BigDecimal.ONE, new BigDecimal("101")));
        assertThrows(IllegalArgumentException.class, () -> BankFinancingFloatingRateCalculator.principalInstalment(
                BigDecimal.ONE, 0, 1));
    }
}
