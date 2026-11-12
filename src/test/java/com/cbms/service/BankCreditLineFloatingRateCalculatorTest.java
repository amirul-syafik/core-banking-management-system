package com.cbms.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class BankCreditLineFloatingRateCalculatorTest {
    @Test
    void rateChangesUseContractualMargin() {
        BigDecimal margin = new BigDecimal("1.25");
        assertEquals(0, new BigDecimal("5.25").compareTo(
                BankCreditLineFloatingRateCalculator.effectiveRate(new BigDecimal("4.00"), margin)));
        assertEquals(0, new BigDecimal("5.75").compareTo(
                BankCreditLineFloatingRateCalculator.effectiveRate(new BigDecimal("4.50"), margin)));
    }

    @Test
    void utilizedPrincipalDrivesDailyInterest() {
        BigDecimal rate = new BigDecimal("5.75");
        assertEquals(0, new BigDecimal("1.5753424658").compareTo(
                BankCreditLineFloatingRateCalculator.dailyInterest(new BigDecimal("10000"), rate)));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                BankCreditLineFloatingRateCalculator.dailyInterest(BigDecimal.ZERO, rate)));
    }

    @Test
    void invalidReferenceOrOutOfRangeRateRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                BankCreditLineFloatingRateCalculator.effectiveRate(null, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () ->
                BankCreditLineFloatingRateCalculator.effectiveRate(BigDecimal.ZERO, new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () ->
                BankCreditLineFloatingRateCalculator.dailyInterest(new BigDecimal("-1"), BigDecimal.ONE));
    }
}
