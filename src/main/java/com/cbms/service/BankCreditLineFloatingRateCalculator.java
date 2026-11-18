package com.cbms.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conventional revolving-credit ACT/365 interest calculation.
 * Principal must be the locked utilized balance at accrual time;
 * a floating contract's reference and margin are snapshotted at origination.
 */
public final class BankCreditLineFloatingRateCalculator {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal DAILY_DIVISOR = new BigDecimal("36500");

    private BankCreditLineFloatingRateCalculator() { }

    public static BigDecimal effectiveRate(BigDecimal baseRatePct, BigDecimal marginPct) {
        if (baseRatePct == null || marginPct == null) {
            throw new IllegalArgumentException("Missing floating rate reference or margin");
        }
        BigDecimal result = baseRatePct.add(marginPct);
        if (result.signum() < 0 || result.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("Floating rate must be between 0% and 100%");
        }
        return result;
    }

    public static BigDecimal dailyInterest(BigDecimal utilizedAmount, BigDecimal annualRatePct) {
        if (utilizedAmount == null || utilizedAmount.signum() < 0
                || annualRatePct == null || annualRatePct.signum() < 0
                || annualRatePct.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("Invalid daily credit-line interest inputs");
        }
        return utilizedAmount.multiply(annualRatePct)
                .divide(DAILY_DIVISOR, 10, RoundingMode.HALF_UP);
    }
}
