package com.cbms;

import com.cbms.service.BankFixedDepositMaturityService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class BankFixedDepositMaturityCalculationTest {

    // The date calculator is pure; no JDBC calls are made by calculate().
    private final BankFixedDepositMaturityService service = new BankFixedDepositMaturityService(null);

    @Test
    void monthEndPlacementRemainsMonthEnd() {
        var result = service.calculate(LocalDate.of(2026, 4, 30), 1, "NONE");
        assertEquals(LocalDate.of(2026, 5, 31), result.contractualMaturityDate());
        assertTrue(result.monthEndPlacement());
    }

    @Test
    void JanuaryMonthEndClampsToFebruaryLastDay() {
        var result = service.calculate(LocalDate.of(2026, 1, 31), 1, "NONE");
        assertEquals(LocalDate.of(2026, 2, 28), result.contractualMaturityDate());
    }

    @Test
    void leapYearMonthEndSurvivesYearlyTenor() {
        var result = service.calculate(LocalDate.of(2024, 2, 29), 12, "NONE");
        assertEquals(LocalDate.of(2025, 2, 28), result.contractualMaturityDate());
    }

    @Test
    void followingConventionMovesSaturdayToMonday() {
        var result = service.calculate(LocalDate.of(2026, 10, 7), 1, "FOLLOWING");
        assertEquals(LocalDate.of(2026, 11, 7), result.contractualMaturityDate());
        assertEquals(LocalDate.of(2026, 11, 9), result.adjustedPaymentDate());
        assertTrue(result.weekendAdjustmentApplied());
    }

    @Test
    void precedingConventionMovesSaturdayToFriday() {
        var result = service.calculate(LocalDate.of(2026, 10, 7), 1, "PRECEDING");
        assertEquals(LocalDate.of(2026, 11, 6), result.adjustedPaymentDate());
    }

    @Test
    void noneConventionDoesNotAdjustWeekend() {
        var result = service.calculate(LocalDate.of(2026, 10, 7), 1, "NONE");
        assertEquals(result.contractualMaturityDate(), result.adjustedPaymentDate());
        assertFalse(result.weekendAdjustmentApplied());
    }

    @Test
    void invalidTenorAndConventionAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(LocalDate.of(2026, 1, 1), 0, "NONE"));
        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(LocalDate.of(2026, 1, 1), 601, "NONE"));
        assertThrows(IllegalArgumentException.class,
                () -> service.calculate(LocalDate.of(2026, 1, 1), 1, "HOLIDAY"));
    }
}
