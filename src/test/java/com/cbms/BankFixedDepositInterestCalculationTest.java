package com.cbms;

import com.cbms.service.BankFixedDepositInterestService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class BankFixedDepositInterestCalculationTest {

    private final BankFixedDepositInterestService service = new BankFixedDepositInterestService();
    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    @Test
    void actual365FixedCalculatesOneYearInterest() {
        var result = service.calculate(new BigDecimal("10000.00"), new BigDecimal("3.65"),
                START, START.plusDays(365), "ACT_365_FIXED");
        assertEquals(365L, result.actualDays());
        assertEquals(0, new BigDecimal("365.00").compareTo(result.totalInterest()));
    }

    @Test
    void actual360Uses360DayDenominator() {
        var result = service.calculate(new BigDecimal("10000.00"), new BigDecimal("3.60"),
                START, START.plusDays(365), "ACT_360");
        assertEquals(0, new BigDecimal("365.00").compareTo(result.totalInterest()));
    }

    @Test
    void leapDayIsCountedInActual365Fixed() {
        var result = service.calculate(new BigDecimal("36500"), new BigDecimal("1.00"),
                LocalDate.of(2024, 2, 28), LocalDate.of(2024, 3, 1), "ACT_365_FIXED");
        assertEquals(2L, result.actualDays());
        assertEquals(0, new BigDecimal("2.00").compareTo(result.totalInterest()));
    }

    @Test
    void cumulativeInterestIsRoundedOnceToCents() {
        var result = service.calculate(new BigDecimal("100.00"), new BigDecimal("1.00"),
                START, START.plusDays(1), "ACT_365_FIXED");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.totalInterest()));
        assertTrue(result.dailyInterest().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void zeroDurationEarnsZeroInterest() {
        var result = service.calculate(new BigDecimal("1000"), new BigDecimal("5"),
                START, START, "ACT_365_FIXED");
        assertEquals(0L, result.actualDays());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.totalInterest()));
    }

    @Test
    void invalidPrincipalRatePeriodAndBasisAreRejected() {
        assertBadRequest(() -> service.calculate(BigDecimal.ZERO, BigDecimal.ONE, START, START.plusDays(1), "ACT_365_FIXED"));
        assertBadRequest(() -> service.calculate(BigDecimal.ONE, new BigDecimal("-1"), START, START.plusDays(1), "ACT_365_FIXED"));
        assertBadRequest(() -> service.calculate(BigDecimal.ONE, BigDecimal.ONE, START.plusDays(1), START, "ACT_365_FIXED"));
        assertBadRequest(() -> service.calculate(BigDecimal.ONE, BigDecimal.ONE, START, START.plusDays(1), "ACT_365"));
    }

    private static void assertBadRequest(org.junit.jupiter.api.function.Executable operation) {
        var failure = assertThrows(ResponseStatusException.class, operation);
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    }
}
