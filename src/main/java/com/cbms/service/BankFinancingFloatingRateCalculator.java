package com.cbms.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Illustrative conventional floating-rate financing schedule: equal monthly principal
 * and monthly reducing-balance interest. Historical and future interest amounts are
 * never recalculated together; only future unpaid instalments may be repriced.
 */
public final class BankFinancingFloatingRateCalculator {
    private static final BigDecimal RATE_DIVISOR = new BigDecimal("1200");

    private BankFinancingFloatingRateCalculator() {}

    public static BigDecimal monthlyInterest(BigDecimal openingPrincipal, BigDecimal annualRate) {
        if (openingPrincipal == null || openingPrincipal.signum() < 0 || annualRate == null
                || annualRate.signum() < 0 || annualRate.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Invalid principal or financing rate");
        }
        return openingPrincipal.multiply(annualRate)
                .divide(RATE_DIVISOR, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal principalInstalment(BigDecimal principal, int months, int instalmentNo) {
        if (principal == null || principal.signum() <= 0 || months < 1
                || instalmentNo < 1 || instalmentNo > months) {
            throw new IllegalArgumentException("Invalid financing principal or instalment");
        }
        BigDecimal amount = principal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal regular = amount.divide(BigDecimal.valueOf(months), 2, RoundingMode.DOWN);
        return instalmentNo == months
                ? amount.subtract(regular.multiply(BigDecimal.valueOf(months - 1)))
                : regular;
    }
}
