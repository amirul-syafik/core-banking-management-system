package com.cbms.dto;

import java.math.BigDecimal;
import java.util.List;

public record CustomerOverviewResponse(
        Long custId, String custNm, String custTyp, String customerStatus,
        AccountSummary accounts, FinancingSummary financing,
        FixedDepositSummary fixedDeposits, CollateralSummary collateral) {

    public record CurrencyAmount(String currencyCd, BigDecimal amount) {
    }

    public record AccountSummary(long total, long active, List<CurrencyAmount> ledgerBalances,
                                 List<CurrencyAmount> availableBalances) {
    }

    public record FinancingSummary(long totalFacilities, long activeFacilities,
                                   List<CurrencyAmount> totalApprovedAmounts) {
    }

    public record FixedDepositSummary(long total, long active,
                                      List<CurrencyAmount> activePrincipalAmounts) {
    }

    public record CollateralSummary(long totalRegistered, long totalInactive,
                                    List<CurrencyAmount> activeAllocatedAmounts) {
    }
}
