package com.cbms.service;

import com.cbms.dto.BankFixedDepositInterestRequest;
import com.cbms.dto.BankFixedDepositInterestResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class BankFixedDepositInterestService {

    public BankFixedDepositInterestResponse calculate(
            BankFixedDepositInterestRequest request
    ) {
        return calculate(
                request.principalAmount(),
                request.annualRate(),
                request.startDate(),
                request.endDate(),
                request.dayCountBasis()
        );
    }

    public BankFixedDepositInterestResponse calculate(
            BigDecimal principal,
            BigDecimal annualRate,
            LocalDate startDate,
            LocalDate endDate,
            String basis
    ) {
        if (principal == null
                || principal.compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest("Principal must be positive");
        }

        if (annualRate == null
                || annualRate.compareTo(BigDecimal.ZERO) < 0) {
            throw badRequest("Interest rate cannot be negative");
        }

        if (startDate == null || endDate == null) {
            throw badRequest("Start and end dates are required");
        }

        if (endDate.isBefore(startDate)) {
            throw badRequest("End date cannot precede start date");
        }

        int denominator;

        if ("ACT_365_FIXED".equals(basis)) {
            denominator = 365;
        } else if ("ACT_360".equals(basis)) {
            denominator = 360;
        } else {
            throw badRequest("Unsupported day-count convention");
        }

        long days = ChronoUnit.DAYS.between(
                startDate,
                endDate
        );

        BigDecimal dailyInterest = principal
                .multiply(annualRate)
                .divide(BigDecimal.valueOf(100), 16,
                        RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(denominator), 12,
                        RoundingMode.HALF_UP);

        // Calculate from the unrounded annual interest,
        // not from dailyInterest rounded to 12 places.
        BigDecimal totalInterest = principal
                .multiply(annualRate)
                .multiply(BigDecimal.valueOf(days))
                .divide(
                        BigDecimal.valueOf(100L * denominator),
                        2,
                        RoundingMode.HALF_UP
                );

        return new BankFixedDepositInterestResponse(
                principal,
                annualRate,
                startDate,
                endDate,
                days,
                basis,
                dailyInterest,
                totalInterest
        );
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}
