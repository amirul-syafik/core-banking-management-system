package com.cbms.service;

import com.cbms.dto.*;
import com.cbms.entity.*;
import com.cbms.repository.*;
import com.cbms.reference.BusinessReferenceGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;

@Service
public class BankCollateralService {
    private static final Set<String> TYPES = Set.of("VEHICLE", "PROPERTY", "CASH", "OTHER");
    private static final Set<String> STATUSES = Set.of("REGISTERED", "INACTIVE");
    private final BankCollateralRepository collaterals;
    private final BankVehicleRepository vehicles;
    private final CustomerRepository customers;
    private final BankFinancingApplicationRepository applications;
    private final BankFinancingCollateralRepository financingLinks;
    private final BankCollateralAllocationLockRepository allocationLocks;
    private final BankVehicleService vehicleService;
    private final JdbcTemplate jdbc;

    public BankCollateralService(BankCollateralRepository c, BankVehicleRepository v, CustomerRepository u,
                                 BankFinancingApplicationRepository applications,
                                 BankFinancingCollateralRepository f, BankCollateralAllocationLockRepository l,
                                 JdbcTemplate jdbc, BankVehicleService vehicleService) {
        collaterals = c;
        vehicles = v;
        customers = u;
        this.applications = applications;
        financingLinks = f;
        allocationLocks = l;
        this.jdbc = jdbc;
        this.vehicleService = vehicleService;
    }

    @Transactional
    public BankCollateralResponse create(BankCollateralRequest request, Authentication auth) {
        if (request.vehicleId() != null)
            throw conflict("Register vehicles through a registered Vehicle-type collateral");
        BankCollateral c = new BankCollateral();
        apply(c, request);
        if (request.originApplId() == null) throw bad("Collateral must originate from an approved Hire Purchase application");
        BankFinancingApplication application = applications.findById(request.originApplId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Financing application not found"));
        if (!"HIRE_PURCHASE".equals(application.getFacTyp()) || !"APPROVED".equals(application.getApplSts()))
            throw conflict("Collateral registration requires an approved Hire Purchase application");
        if (!application.getCustId().equals(c.getOwner().getCustId()))
            throw conflict("Collateral owner must be the financing applicant");
        c.setOriginApplId(application.getApplId());
        c.setCollateralSts("REGISTERED");
        OffsetDateTime now = OffsetDateTime.now();
        c.setCollateralRef(BusinessReferenceGenerator.next(jdbc, BusinessReferenceGenerator.Stream.COLLATERAL, now));
        c.setCrtDt(now);
        c.setCrtBy(actor(auth));
        c.setUpdDt(now);
        c.setUpdBy(actor(auth));
        return save(c);
    }

    @Transactional
    public BankCollateralResponse update(Long id, BankCollateralRequest request, Authentication auth) {
        BankCollateral c = allocationLocks.lock(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collateral not found"));
        if (!"REGISTERED".equals(c.getCollateralSts())) throw conflict("Only registered collateral may be updated");
        if (financingLinks.existsByCollateral_CollateralIdAndLinkStsIn(id, java.util.List.of("PROPOSED", "ACTIVE")))
            throw conflict("Collateral with a proposed or active financing assignment cannot be edited");
        if (c.getOriginApplId() == null || !c.getOriginApplId().equals(request.originApplId()))
            throw conflict("Collateral financing origin cannot be changed");
        Long currentVehicleId = c.getVehicle() == null ? null : c.getVehicle().getVehicleId();
        if (!java.util.Objects.equals(currentVehicleId, request.vehicleId()))
            throw conflict("Vehicle links can only be established through vehicle registration");
        BankFinancingApplication origin = applications.findById(c.getOriginApplId())
                .orElseThrow(() -> conflict("Origin financing application is unavailable"));
        if (!origin.getCustId().equals(request.ownerCustId()))
            throw conflict("Collateral owner must remain the financing applicant");
        apply(c, request);
        c.setUpdDt(OffsetDateTime.now());
        c.setUpdBy(actor(auth));
        return save(c);
    }

    @Transactional
    public BankCollateralResponse changeStatus(Long id, BankCollateralStatusRequest request, Authentication auth) {
        BankCollateral c = allocationLocks.lock(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collateral not found"));
        String next = allowed(request.collateralSts(), STATUSES, "collateralSts");
        if ("INACTIVE".equals(next) && financingLinks.existsByCollateral_CollateralIdAndLinkStsIn(id, java.util.List.of("PROPOSED", "ACTIVE")))
            throw conflict("Cannot deactivate collateral with a proposed or active financing assignment");
        if (next.equals(c.getCollateralSts())) return map(c);
        if ("REGISTERED".equals(next) && c.getVehicle() != null &&
                collaterals.existsByVehicle_VehicleIdAndCollateralStsAndCollateralIdNot(c.getVehicle().getVehicleId(), "REGISTERED", id))
            throw conflict("Vehicle already has a registered collateral record");
        // Financing-linked collateral must be protected from deactivation.
        c.setCollateralSts(next);
        c.setUpdDt(OffsetDateTime.now());
        c.setUpdBy(actor(auth));
        return save(c);
    }

    @Transactional(readOnly = true)
    public BankCollateralResponse get(Long id) {
        return map(find(id));
    }

    @Transactional
    public BankVehicleResponse registerVehicle(Long collateralId, BankVehicleRegistrationRequest request, Authentication auth) {
        BankCollateral collateral = allocationLocks.lock(collateralId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collateral not found"));
        if (!"REGISTERED".equals(collateral.getCollateralSts()))
            throw conflict("Vehicle can only be registered against registered collateral");
        if (!"VEHICLE".equals(collateral.getCollateralTyp()))
            throw conflict("Vehicle details apply only to Vehicle-type collateral");
        if (collateral.getVehicle() != null)
            throw conflict("A vehicle is already registered for this collateral");
        if (collateral.getOriginApplId() == null)
            throw conflict("Collateral has no financing application origin");
        BankFinancingApplication application = applications.findById(collateral.getOriginApplId())
                .orElseThrow(() -> conflict("Origin financing application is unavailable"));
        if (!"HIRE_PURCHASE".equals(application.getFacTyp())
                || !Set.of("APPROVED", "COMPLETED").contains(application.getApplSts()))
            throw conflict("Vehicle registration requires an approved Hire Purchase application");
        if (!application.getCustId().equals(collateral.getOwner().getCustId()))
            throw conflict("Collateral owner no longer matches the financing customer");
        BankVehicleResponse vehicle = vehicleService.createForCollateral(collateral.getOwner().getCustId(), request, auth);
        collateral.setVehicle(vehicles.findById(vehicle.vehicleId()).orElseThrow());
        collateral.setUpdDt(OffsetDateTime.now());
        collateral.setUpdBy(actor(auth));
        save(collateral);
        return vehicle;
    }

    @Transactional(readOnly = true)
    public Page<BankCollateralResponse> search(Long ownerCustId, Long vehicleId, String collateralTyp, String collateralSts, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw bad("page must be >= 0 and size between 1 and 100");
        if (ownerCustId != null && ownerCustId <= 0 || vehicleId != null && vehicleId <= 0)
            throw bad("IDs must be positive");
        Specification<BankCollateral> spec = (root, q, cb) -> cb.conjunction();
        if (ownerCustId != null)
            spec = spec.and((root, q, cb) -> cb.equal(root.get("owner").get("custId"), ownerCustId));
        if (vehicleId != null)
            spec = spec.and((root, q, cb) -> cb.equal(root.get("vehicle").get("vehicleId"), vehicleId));
        if (collateralTyp != null && !collateralTyp.isBlank()) {
            String typ = allowed(collateralTyp, TYPES, "collateralTyp");
            spec = spec.and((root, q, cb) -> cb.equal(root.get("collateralTyp"), typ));
        }
        if (collateralSts != null && !collateralSts.isBlank()) {
            String sts = allowed(collateralSts, STATUSES, "collateralSts");
            spec = spec.and((root, q, cb) -> cb.equal(root.get("collateralSts"), sts));
        }
        return collaterals.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "collateralId"))).map(this::map);
    }

    private void apply(BankCollateral c, BankCollateralRequest r) {
        String typ = allowed(r.collateralTyp(), TYPES, "collateralTyp");
        if (r.ownerCustId() == null || r.ownerCustId() <= 0) throw bad("ownerCustId must be positive");
        Customer owner = customers.findById(r.ownerCustId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        BankVehicle vehicle = null;
        if ("VEHICLE".equals(typ)) {
            if (r.vehicleId() != null) {
                vehicle = vehicles.lockById(r.vehicleId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
                if (!"ACTIVE".equals(vehicle.getVehicleSts())) throw conflict("Vehicle must be ACTIVE");
                if (!vehicle.getOwner().getCustId().equals(owner.getCustId()))
                    throw bad("Vehicle owner does not match collateral owner");
            }
            if (vehicle != null && collaterals.existsByVehicle_VehicleIdAndCollateralSts(vehicle.getVehicleId(), "REGISTERED") &&
                    (c.getCollateralId() == null || !vehicle.getVehicleId().equals(c.getVehicle() == null ? null : c.getVehicle().getVehicleId())))
                throw conflict("Vehicle already has a registered collateral record");
        } else if (r.vehicleId() != null) throw bad("vehicleId is only valid for VEHICLE collateral");
        if (r.collateralDesc() == null || r.collateralDesc().isBlank()) throw bad("collateralDesc is required");
        c.setCollateralTyp(typ);
        c.setCollateralDesc(r.collateralDesc().trim());
        c.setOwner(owner);
        c.setVehicle(vehicle);
    }

    private BankCollateralResponse save(BankCollateral c) {
        try {
            return map(collaterals.saveAndFlush(c));
        } catch (DataIntegrityViolationException e) {
            throw conflict("Collateral conflicts with an existing record or violates database constraints");
        }
    }

    private BankCollateral find(Long id) {
        if (id == null || id <= 0) throw bad("collateralId must be positive");
        return collaterals.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collateral not found"));
    }

    private BankCollateralResponse map(BankCollateral c) {
        BankVehicle v = c.getVehicle();
        return new BankCollateralResponse(c.getCollateralId(), c.getCollateralRef(), c.getCollateralTyp(), c.getCollateralDesc(),
                c.getOriginApplId(),
                c.getOwner().getCustId(), c.getOwner().getCustNm(), v == null ? null : v.getVehicleId(), v == null ? null : v.getChassisNo(),
                c.getCollateralSts(), c.getCrtDt(), c.getCrtBy(), c.getUpdDt(), c.getUpdBy());
    }

    private static String allowed(String v, Set<String> values, String field) {
        if (v == null || !values.contains(v.trim().toUpperCase(Locale.ROOT))) throw bad("Invalid " + field);
        return v.trim().toUpperCase(Locale.ROOT);
    }

    private static String actor(Authentication a) {
        if (a == null || !a.isAuthenticated() || a.getName() == null || a.getName().isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        if (a.getName().length() > 100) throw bad("Username too long");
        return a.getName();
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }

    private static ResponseStatusException conflict(String m) {
        return new ResponseStatusException(HttpStatus.CONFLICT, m);
    }
}
