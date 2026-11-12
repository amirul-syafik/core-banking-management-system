package com.cbms.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BankFloatingDepositRateCalculatorTest {
    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    @Test
    void rateChangesAccrueUsingHistoricalSegmentsRatherThanCurrentRateForAllDays() {
        var rateHistory = List.of(
                new BankFloatingDepositRateCalculator.RateVersion(START.minusDays(10), new BigDecimal("6.00")),
                new BankFloatingDepositRateCalculator.RateVersion(START.plusDays(10), new BigDecimal("6.50")));
        var result = BankFloatingDepositRateCalculator.calculate(
                new BigDecimal("10000"), new BigDecimal("0.50"), BigDecimal.ZERO,
                START, START.plusDays(20), rateHistory);
        // 10 days at 6.50%, followed by 10 days at 7.00%.
        assertEquals(0, new BigDecimal("36.99").compareTo(result.totalInterest()));
        assertEquals(0, new BigDecimal("7.00").compareTo(result.currentRatePct()));
        assertEquals(0, new BigDecimal("6.75000").compareTo(result.weightedRatePct()));
    }

    @Test
    void penaltyFloorsEachApplicableRateAtZeroRatherThanCreatingNegativeInterest() {
        var history = List.of(new BankFloatingDepositRateCalculator.RateVersion(
                START.minusDays(1), new BigDecimal("1.00")));
        var result = BankFloatingDepositRateCalculator.calculate(
                new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("2.00"),
                START, START.plusDays(30), history);
        assertEquals(0, BigDecimal.ZERO.compareTo(result.totalInterest()));
    }

    @Test
    void rejectsMissingRateAtStartOfDeposit() {
        var history = List.of(new BankFloatingDepositRateCalculator.RateVersion(
                START.plusDays(1), new BigDecimal("3.00")));
        assertThrows(IllegalArgumentException.class, () -> BankFloatingDepositRateCalculator.calculate(
                new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO,
                START, START.plusDays(2), history));
    }

    @Test
    void oldRateVersionBeforeContractIsOverriddenByLastApplicableVersion() {
        var history = List.of(
                new BankFloatingDepositRateCalculator.RateVersion(START.minusDays(365), new BigDecimal("2.00")),
                new BankFloatingDepositRateCalculator.RateVersion(START.minusDays(30), new BigDecimal("4.00")));
        var result = BankFloatingDepositRateCalculator.calculate(
                new BigDecimal("36500"), BigDecimal.ZERO, BigDecimal.ZERO,
                START, START.plusDays(1), history);
        assertEquals(0, new BigDecimal("4.00").compareTo(result.totalInterest()));
    }
}
