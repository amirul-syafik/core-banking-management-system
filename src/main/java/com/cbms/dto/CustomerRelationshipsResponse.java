package com.cbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CustomerRelationshipsResponse(Long custId, String custNm,
                                            List<Account> accounts, List<Facility> financingFacilities,
                                            List<Deposit> fixedDeposits,
                                            List<Vehicle> vehicles, List<Collateral> collaterals) {
    public record Account(Long acctId, String acctNo, String acctTyp, String ccyCd, String status, LocalDate openDt,
                          BigDecimal ledgerBalance, BigDecimal availableBalance) {
    }

    public record Facility(Long facId, String facRef, Long applId, String facTyp, String ccyCd,
                           BigDecimal approvedAmount, Integer tenorMonths, BigDecimal effectiveRate, String status,
                           LocalDate bookDate, LocalDate maturityDate) {
    }

    public record Deposit(Long fdId, String fdRef, String fdTyp, String ccyCd, BigDecimal principalAmount,
                          Integer tenorMonths, LocalDate placementDate, LocalDate maturityDate, BigDecimal interestRate,
                          String status) {
    }

    public record Vehicle(Long vehicleId, String registrationNo, String chassisNo, String make, String model,
                          Integer manufactureYear, String status) {
    }

    public record Collateral(Long collateralId, String collateralRef, String collateralType, String description,
                             String status, Long vehicleId, BigDecimal latestForcedSaleValue,
                             BigDecimal activeAllocatedAmount) {
    }
}
