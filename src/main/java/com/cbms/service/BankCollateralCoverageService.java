package com.cbms.service;

import com.cbms.dto.*;
import com.cbms.entity.*;
import com.cbms.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class BankCollateralCoverageService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final int STALE_AFTER_DAYS = 365;
    private final BankFinancingFacilityCollateralRepository facilities;
    private final BankFinancingCollateralRepository links;
    private final BankCollateralCoverageReadRepository collateralReader;
    private final BankCollateralValuationRepository valuations;

    public BankCollateralCoverageService(BankFinancingFacilityCollateralRepository facilities,
                                         BankFinancingCollateralRepository links,
                                         BankCollateralCoverageReadRepository collateralReader,
                                         BankCollateralValuationRepository valuations) {
        this.facilities = facilities;
        this.links = links;
        this.collateralReader = collateralReader;
        this.valuations = valuations;
    }

    private void validId(Long id) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID must be positive");
    }

    private boolean stale(LocalDate date) {
        return date == null || date.isBefore(LocalDate.now().minusDays(STALE_AFTER_DAYS));
    }

    @Transactional(readOnly = true)
    public BankCollateralCoverageResponse facilityCoverage(Long facId) {
        validId(facId);
        BankFinancingFacility facility = facilities.findById(facId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Facility not found"));
        List<BankCollateralCoverageItemResponse> items = new ArrayList<>();
        BigDecimal allocated = ZERO;
        boolean hasStale = false;
        boolean hasInactive = false;
        for (BankFinancingCollateral link : links.findByFacility_FacIdOrderByLinkIdDesc(facId)) {
            if (!"ACTIVE".equals(link.getLinkSts())) continue;
            BankCollateral collateral = link.getCollateral();
            BankCollateralValuation latest = valuations
                    .findFirstByCollateral_CollateralIdOrderByValuationDtDescValuationIdDesc(collateral.getCollateralId())
                    .orElse(null);
            LocalDate valuationDate = latest == null ? null : latest.getValuationDt();
            boolean isStale = stale(valuationDate);
            hasStale |= isStale;
            hasInactive |= !"REGISTERED".equals(collateral.getCollateralSts());
            items.add(new BankCollateralCoverageItemResponse(link.getLinkId(), collateral.getCollateralId(),
                    collateral.getCollateralRef(), link.getAllocatedAmount(),
                    latest == null ? null : latest.getForcedSaleValue(), valuationDate,
                    isStale, collateral.getCollateralSts()));
            allocated = allocated.add(link.getAllocatedAmount());
        }
        BigDecimal approved = facility.getApprAmt();
        if (approved == null || approved.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Facility approved amount must be positive");
        }
        BigDecimal pct = allocated.multiply(new BigDecimal("100"))
                .divide(approved, 2, RoundingMode.HALF_UP);
        BigDecimal shortfall = approved.subtract(allocated).max(ZERO);
        BigDecimal surplus = allocated.subtract(approved).max(ZERO);
        String status = allocated.signum() == 0 ? "UNSECURED" : shortfall.signum() > 0 ? "UNDER_COVERED" : "COVERED";
        // Warnings override the monetary status; the underlying amounts remain visible.
        if (hasInactive) status = "INACTIVE_COLLATERAL";
        else if (hasStale) status = "STALE_VALUATION";
        return new BankCollateralCoverageResponse(facility.getFacId(), facility.getFacRef(),
                facility.getFacSts(), facility.getCcyCd(), approved, allocated, pct,
                shortfall, surplus, status, List.copyOf(items));
    }

    @Transactional(readOnly = true)
    public BankCollateralExposureResponse collateralExposure(Long collateralId) {
        validId(collateralId);
        // This repository's lock() method is pessimistic: do not use it for a read-only endpoint.
        // Read collateral through existing link entity if available, or a plain JPA lookup below.
        BankCollateral collateral = collateralLookup(collateralId);
        BankCollateralValuation latest = valuations
                .findFirstByCollateral_CollateralIdOrderByValuationDtDescValuationIdDesc(collateralId)
                .orElse(null);
        BigDecimal fsv = latest == null ? ZERO : latest.getForcedSaleValue();
        BigDecimal allocated = ZERO;
        long count = 0;
        for (BankFinancingCollateral link : links.findByCollateral_CollateralIdOrderByLinkIdDesc(collateralId)) {
            if ("ACTIVE".equals(link.getLinkSts())) {
                allocated = allocated.add(link.getAllocatedAmount());
                count++;
            }
        }
        BigDecimal available = fsv.subtract(allocated).max(ZERO);
        BigDecimal excess = allocated.subtract(fsv).max(ZERO);
        boolean isStale = stale(latest == null ? null : latest.getValuationDt());
        String status = latest == null ? "NO_VALUATION" :
                !"REGISTERED".equals(collateral.getCollateralSts()) ? "INACTIVE_COLLATERAL" :
                        isStale ? "STALE_VALUATION" : excess.signum() > 0 ? "OVER_ALLOCATED" : "WITHIN_LIMIT";
        return new BankCollateralExposureResponse(collateralId, collateral.getCollateralRef(),
                collateral.getCollateralSts(), latest == null ? null : fsv,
                latest == null ? null : latest.getValuationDt(), isStale, allocated,
                latest == null ? null : available, latest == null ? null : excess, count, status);
    }

    private BankCollateral collateralLookup(Long id) {
        // Use standard findById without obtaining an exclusive allocation lock.
        return collateralReader.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collateral not found"));
    }

}
