package com.cbms.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * ACT/365 accrued-interest calculator using effective-dated base-rate history.
 * The contractual base code and margin are captured at placement/renewal;
 * later changes to the scheme's margin must not reprice an existing contract.
 * This is a conventional-interest calculation, NOT Islamic profit accounting.
 */
public final class BankFloatingDepositRateCalculator {
    private static final BigDecimal DIVISOR = new BigDecimal("36500");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private BankFloatingDepositRateCalculator() { }

    public record RateVersion(LocalDate effectiveDate, BigDecimal baseRatePct) { }

    public record Result(BigDecimal totalInterest, BigDecimal dailyInterest,
                         BigDecimal currentRatePct, BigDecimal weightedRatePct) { }

    /** Accrual period is [startDate, endExclusive), and the rate at each day's start applies. */
    public static Result calculate(BigDecimal principal, BigDecimal marginPct,
                                   BigDecimal annualPenaltyPct, LocalDate startDate,
                                   LocalDate endExclusive, List<RateVersion> baseHistory) {
        if (principal == null || principal.signum() <= 0 || marginPct == null
                || annualPenaltyPct == null || annualPenaltyPct.signum() < 0
                || startDate == null || endExclusive == null || !endExclusive.isAfter(startDate)
                || baseHistory == null || baseHistory.isEmpty()) {
            throw new IllegalArgumentException("Invalid floating-rate interest calculation parameters");
        }

        List<RateVersion> versions = baseHistory.stream()
                .sorted(Comparator.comparing(RateVersion::effectiveDate))
                .toList();
        BigDecimal unrounded = BigDecimal.ZERO;
        BigDecimal weightedRateDays = BigDecimal.ZERO;
        BigDecimal lastRate = null;
        LocalDate cursor = startDate;

        for (int i = 0; i < versions.size(); i++) {
            RateVersion version = versions.get(i);
            if (version.effectiveDate() == null || version.baseRatePct() == null) {
                throw new IllegalArgumentException("Invalid base rate history");
            }
            if (version.effectiveDate().isAfter(cursor)) {
                // Do not silently interpolate rates before their first effective date.
                if (i == 0) {
                    throw new IllegalArgumentException("No applicable base rate for deposit start date");
                }
                continue;
            }
            LocalDate nextDate = i + 1 < versions.size()
                    ? versions.get(i + 1).effectiveDate() : endExclusive;
            LocalDate segmentEnd = nextDate.isBefore(endExclusive) ? nextDate : endExclusive;
            if (!segmentEnd.isAfter(cursor)) continue;

            BigDecimal contractual = version.baseRatePct().add(marginPct);
            if (contractual.signum() < 0 || contractual.compareTo(ONE_HUNDRED) > 0) {
                throw new IllegalArgumentException("Floating rate is outside 0%-100% bounds");
            }
            BigDecimal applicable = contractual.subtract(annualPenaltyPct).max(BigDecimal.ZERO);
            long days = ChronoUnit.DAYS.between(cursor, segmentEnd);
            weightedRateDays = weightedRateDays.add(applicable.multiply(BigDecimal.valueOf(days)));
            unrounded = unrounded.add(principal.multiply(applicable)
                    .multiply(BigDecimal.valueOf(days))
                    .divide(DIVISOR, 18, RoundingMode.HALF_UP));
            lastRate = applicable;
            cursor = segmentEnd;
            if (!cursor.isBefore(endExclusive)) break;
        }
        if (cursor.isBefore(endExclusive) || lastRate == null) {
            throw new IllegalArgumentException("Incomplete floating rate history");
        }
        long totalDays = ChronoUnit.DAYS.between(startDate, endExclusive);
        return new Result(
                unrounded.setScale(2, RoundingMode.HALF_UP),
                principal.multiply(lastRate).divide(DIVISOR, 12, RoundingMode.HALF_UP),
                lastRate,
                weightedRateDays.divide(BigDecimal.valueOf(totalDays), 5, RoundingMode.HALF_UP));
    }
}
