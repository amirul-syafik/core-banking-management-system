package com.cbms.service;

import com.cbms.dto.*;
import com.cbms.entity.*;
import com.cbms.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class BankFinancingCollateralService {
    private final BankFinancingCollateralRepository links;
    private final BankFinancingApplicationCollateralRepository applications;
    private final BankFinancingFacilityCollateralRepository facilities;
    private final BankCollateralAllocationLockRepository collaterals;
    private final BankCollateralValuationRepository valuations;

    public BankFinancingCollateralService(BankFinancingCollateralRepository l, BankFinancingApplicationCollateralRepository a, BankFinancingFacilityCollateralRepository f, BankCollateralAllocationLockRepository c, BankCollateralValuationRepository v) {
        links = l;
        applications = a;
        facilities = f;
        collaterals = c;
        valuations = v;
    }

    private ResponseStatusException err(HttpStatus s, String m) {
        return new ResponseStatusException(s, m);
    }

    private String actor(Authentication a) {
        return a == null || a.getName() == null ? "SYSTEM" : a.getName();
    }

    private void validId(Long id) {
        if (id == null || id <= 0) throw err(HttpStatus.BAD_REQUEST, "ID must be positive");
    }

    private void validAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2)
            throw err(HttpStatus.BAD_REQUEST, "allocatedAmount must be positive with at most 2 decimal places");
    }

    private BankFinancingCollateral find(Long id) {
        validId(id);
        return links.findById(id).orElseThrow(() -> err(HttpStatus.NOT_FOUND, "Link not found"));
    }

    private BankFinancingCollateralResponse map(BankFinancingCollateral l) {
        return new BankFinancingCollateralResponse(l.getLinkId(), l.getApplication().getApplId(), l.getApplication().getApplRef(), l.getFacility() == null ? null : l.getFacility().getFacId(), l.getFacility() == null ? null : l.getFacility().getFacRef(), l.getCollateral().getCollateralId(), l.getCollateral().getCollateralRef(), l.getAllocatedAmount(), l.getLinkSts(), l.getLinkedDt(), l.getActivatedDt(), l.getReleasedDt(), l.getRemarks(), l.getCrtBy());
    }

    @Transactional
    public BankFinancingCollateralResponse propose(BankFinancingCollateralRequest r, Authentication auth) {
        validId(r.applId());
        validId(r.collateralId());
        validAmount(r.allocatedAmount());
        BankFinancingApplication a = applications.findById(r.applId()).orElseThrow(() -> err(HttpStatus.NOT_FOUND, "Application not found"));
        BankCollateral c = collaterals.lock(r.collateralId()).orElseThrow(() -> err(HttpStatus.NOT_FOUND, "Collateral not found"));
        if (!"REGISTERED".equals(c.getCollateralSts())) throw err(HttpStatus.CONFLICT, "Collateral must be REGISTERED");
        if (!a.getCustId().equals(c.getOwner().getCustId()))
            throw err(HttpStatus.CONFLICT, "Applicant must match collateral owner (third-party collateral not supported)");
        if (List.of("REJECTED", "CANCELLED", "WITHDRAWN").contains(a.getApplSts().toUpperCase(Locale.ROOT)))
            throw err(HttpStatus.CONFLICT, "Application is not eligible");
        if (links.existsByApplication_ApplIdAndCollateral_CollateralIdAndLinkStsIn(r.applId(), r.collateralId(), List.of("PROPOSED", "ACTIVE")))
            throw err(HttpStatus.CONFLICT, "Active or proposed link already exists");
        var latest = valuations.findFirstByCollateral_CollateralIdOrderByValuationDtDescValuationIdDesc(r.collateralId()).orElseThrow(() -> err(HttpStatus.CONFLICT, "Collateral valuation is required"));
        if (!"MYR".equalsIgnoreCase(a.getCcyCd()) || !"MYR".equalsIgnoreCase(latest.getCurrencyCd()))
            throw err(HttpStatus.CONFLICT, "Only MYR financing and valuations are supported");
        if (r.allocatedAmount().compareTo(latest.getForcedSaleValue()) > 0)
            throw err(HttpStatus.CONFLICT, "Allocation exceeds latest forced-sale value");
        BankFinancingCollateral l = new BankFinancingCollateral();
        l.setApplication(a);
        l.setCollateral(c);
        l.setAllocatedAmount(r.allocatedAmount());
        l.setLinkSts("PROPOSED");
        l.setLinkedDt(OffsetDateTime.now());
        l.setUpdDt(l.getLinkedDt());
        l.setCrtBy(actor(auth));
        l.setUpdBy(actor(auth));
        l.setRemarks(r.remarks() == null ? null : r.remarks().trim());
        try {
            return map(links.saveAndFlush(l));
        } catch (DataIntegrityViolationException e) {
            throw err(HttpStatus.CONFLICT, "Duplicate or invalid collateral linkage");
        }
    }

    @Transactional
    public BankFinancingCollateralResponse activate(Long linkId, Authentication auth) {
        // Lock collateral first: serialize competing allocations for the same asset.
        BankFinancingCollateral snapshot = find(linkId);
        Long collateralId = snapshot.getCollateral().getCollateralId();
        BankCollateral c = collaterals.lock(collateralId).orElseThrow(() -> err(HttpStatus.NOT_FOUND, "Collateral not found"));
        BankFinancingCollateral l = find(linkId);
        if (!"PROPOSED".equals(l.getLinkSts())) throw err(HttpStatus.CONFLICT, "Only PROPOSED links may be activated");
        if (!"REGISTERED".equals(c.getCollateralSts())) throw err(HttpStatus.CONFLICT, "Collateral is not registered");
        BankFinancingApplication a = applications.findById(l.getApplication().getApplId()).orElseThrow(() -> err(HttpStatus.NOT_FOUND, "Application not found"));
        if (a.getFacId() == null) throw err(HttpStatus.CONFLICT, "Application has no financing facility yet");
        BankFinancingFacility f = facilities.findById(a.getFacId()).orElseThrow(() -> err(HttpStatus.NOT_FOUND, "Facility not found"));
        if (!a.getApplId().equals(f.getApplId()) || !a.getCustId().equals(f.getCustId()))
            throw err(HttpStatus.CONFLICT, "Facility does not belong to this application/customer");
        if (List.of("CANCELLED", "CLOSED", "REJECTED").contains(f.getFacSts().toUpperCase(Locale.ROOT)))
            throw err(HttpStatus.CONFLICT, "Facility not eligible");
        var latest = valuations.findFirstByCollateral_CollateralIdOrderByValuationDtDescValuationIdDesc(collateralId).orElseThrow(() -> err(HttpStatus.CONFLICT, "Valuation required"));
        if (!"MYR".equalsIgnoreCase(f.getCcyCd()) || !"MYR".equalsIgnoreCase(latest.getCurrencyCd()))
            throw err(HttpStatus.CONFLICT, "Currency mismatch");
        BigDecimal used = links.activeAllocated(collateralId, null);
        if (used.add(l.getAllocatedAmount()).compareTo(latest.getForcedSaleValue()) > 0)
            throw err(HttpStatus.CONFLICT, "Total active allocation exceeds forced-sale value");
        l.setFacility(f);
        l.setLinkSts("ACTIVE");
        l.setActivatedDt(OffsetDateTime.now());
        l.setUpdDt(l.getActivatedDt());
        l.setUpdBy(actor(auth));
        return map(links.saveAndFlush(l));
    }

    @Transactional
    public BankFinancingCollateralResponse release(Long linkId, BankFinancingCollateralReleaseRequest request, Authentication auth) {
        validId(linkId);
        if (request == null || request.reason() == null || request.reason().isBlank())
            throw err(HttpStatus.BAD_REQUEST, "Release reason required");
        String reason = request.reason().trim();
        if (reason.length() > 500) throw err(HttpStatus.BAD_REQUEST, "Release reason exceeds 500 characters");
        // Same collateral-row lock used by activation: prevents concurrent release/activation races.
        BankFinancingCollateral snapshot = find(linkId);
        BankCollateral c = collaterals.lock(snapshot.getCollateral().getCollateralId())
                .orElseThrow(() -> err(HttpStatus.NOT_FOUND, "Collateral not found"));
        BankFinancingCollateral l = find(linkId);
        if ("RELEASED".equals(l.getLinkSts())) throw err(HttpStatus.CONFLICT, "Link already released");
        if (!List.of("PROPOSED", "ACTIVE").contains(l.getLinkSts()))
            throw err(HttpStatus.CONFLICT, "Link cannot be released from current status");
        if ("ACTIVE".equals(l.getLinkSts())) {
            if (l.getFacility() == null) throw err(HttpStatus.CONFLICT, "Active link has no facility");
            // Do not free security while financing is outstanding. DRAFT/ACTIVE facilities cannot discharge.
            String status = l.getFacility().getFacSts();
            if (status == null || !List.of("CLOSED", "COMPLETED").contains(status.toUpperCase(Locale.ROOT)))
                throw err(HttpStatus.CONFLICT, "Facility must be CLOSED or COMPLETED before collateral discharge");
        }
        OffsetDateTime now = OffsetDateTime.now();
        l.setLinkSts("RELEASED");
        l.setReleasedDt(now);
        l.setUpdDt(now);
        l.setUpdBy(actor(auth));
        l.setRemarks(reason);
        return map(links.saveAndFlush(l));
    }

    @Transactional(readOnly = true)
    public BankFinancingCollateralResponse get(Long id) {
        return map(find(id));
    }

    @Transactional(readOnly = true)
    public List<BankFinancingCollateralResponse> byApplication(Long id) {
        validId(id);
        return links.findByApplication_ApplIdOrderByLinkIdDesc(id).stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public List<BankFinancingCollateralResponse> byFacility(Long id) {
        validId(id);
        return links.findByFacility_FacIdOrderByLinkIdDesc(id).stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public List<BankFinancingCollateralResponse> byCollateral(Long id) {
        validId(id);
        return links.findByCollateral_CollateralIdOrderByLinkIdDesc(id).stream().map(this::map).toList();
    }
}
