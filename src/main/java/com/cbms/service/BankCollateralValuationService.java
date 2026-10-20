package com.cbms.service;

import com.cbms.dto.*;
import com.cbms.entity.*;
import com.cbms.repository.*;
import com.cbms.security.CurrentActor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class BankCollateralValuationService {
    private final BankCollateralValuationRepository valuations;
    private final BankCollateralValuationLockRepository collaterals;

    public BankCollateralValuationService(BankCollateralValuationRepository valuations, BankCollateralValuationLockRepository collaterals) {
        this.valuations = valuations;
        this.collaterals = collaterals;
    }

    @Transactional
    public BankCollateralValuationResponse create(Long collateralId, BankCollateralValuationRequest request, Authentication auth) {
        requireId(collateralId);
        BankCollateral collateral = collaterals.lockCollateral(collateralId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Collateral not found"));
        if (!"REGISTERED".equals(collateral.getCollateralSts()))
            throw error(HttpStatus.CONFLICT, "Collateral must be REGISTERED");
        if (request.valuationDt().isAfter(LocalDate.now()))
            throw error(HttpStatus.BAD_REQUEST, "Future valuation dates are not allowed");
        if (request.marketValue().signum() <= 0 || request.forcedSaleValue().signum() < 0 || request.forcedSaleValue().compareTo(request.marketValue()) > 0)
            throw error(HttpStatus.BAD_REQUEST, "Forced sale value must be between zero and market value; market value must be positive");
        boolean hasHistory = valuations.existsByCollateral_CollateralId(collateralId);
        String type = hasHistory ? "REVALUATION" : "INITIAL";
        if (hasHistory) {
            BankCollateralValuation latest = valuations.findFirstByCollateral_CollateralIdOrderByValuationDtDescValuationIdDesc(collateralId).orElseThrow();
            if (request.valuationDt().isBefore(latest.getValuationDt()))
                throw error(HttpStatus.CONFLICT, "Revaluation date cannot precede the latest valuation date");
        }
        BankCollateralValuation v = new BankCollateralValuation();
        v.setCollateral(collateral);
        v.setValuationTyp(type);
        v.setValuationDt(request.valuationDt());
        v.setMarketValue(request.marketValue());
        v.setForcedSaleValue(request.forcedSaleValue());
        v.setCurrencyCd("MYR");
        v.setValuationSource(request.valuationSource().trim());
        v.setRemarks(request.remarks() == null ? null : request.remarks().trim());
        v.setCrtDt(OffsetDateTime.now());
        v.setCrtBy(actor(auth));
        return map(valuations.saveAndFlush(v));
    }

    @Transactional(readOnly = true)
    public BankCollateralValuationResponse latest(Long collateralId) {
        requireCollateral(collateralId);
        return valuations.findFirstByCollateral_CollateralIdOrderByValuationDtDescValuationIdDesc(collateralId)
                .map(this::map).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "No valuation recorded for collateral"));
    }

    @Transactional(readOnly = true)
    public BankCollateralValuationResponse get(Long collateralId, Long valuationId) {
        requireCollateral(collateralId);
        requireId(valuationId);
        BankCollateralValuation v = valuations.findById(valuationId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Valuation not found"));
        if (!v.getCollateral().getCollateralId().equals(collateralId))
            throw error(HttpStatus.NOT_FOUND, "Valuation not found for collateral");
        return map(v);
    }

    @Transactional(readOnly = true)
    public Page<BankCollateralValuationResponse> history(Long collateralId, int page, int size) {
        requireCollateral(collateralId);
        if (page < 0 || size < 1 || size > 100)
            throw error(HttpStatus.BAD_REQUEST, "page must be >= 0 and size between 1 and 100");
        return valuations.findByCollateral_CollateralId(collateralId, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("valuationDt"), Sort.Order.desc("valuationId")))).map(this::map);
    }

    private void requireCollateral(Long id) {
        requireId(id);
        if (!collaterals.existsById(id)) throw error(HttpStatus.NOT_FOUND, "Collateral not found");
    }

    private void requireId(Long id) {
        if (id == null || id <= 0) throw error(HttpStatus.BAD_REQUEST, "ID must be positive");
    }

    private BankCollateralValuationResponse map(BankCollateralValuation v) {
        return new BankCollateralValuationResponse(v.getValuationId(), v.getCollateral().getCollateralId(),
                v.getCollateral().getCollateralRef(), v.getValuationTyp(), v.getValuationDt(), v.getMarketValue(),
                v.getForcedSaleValue(), v.getCurrencyCd(), v.getValuationSource(), v.getRemarks(), v.getCrtDt(), v.getCrtBy());
    }

    private String actor(Authentication auth) {
        return CurrentActor.username();
    }

    private ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
