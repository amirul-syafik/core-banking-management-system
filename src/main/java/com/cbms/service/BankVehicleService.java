package com.cbms.service;

import com.cbms.dto.*;
import com.cbms.entity.BankVehicle;
import com.cbms.entity.Customer;
import com.cbms.repository.BankVehicleRepository;
import com.cbms.repository.CustomerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.criteria.JoinType;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;

@Service
public class BankVehicleService {
    private static final Set<String> TYPES = Set.of("CAR", "MOTORCYCLE", "VAN", "LORRY", "BUS", "OTHER");
    private static final Set<String> CONDITIONS = Set.of("NEW", "USED", "RECOND");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private final BankVehicleRepository vehicles;
    private final CustomerRepository customers;

    public BankVehicleService(BankVehicleRepository vehicles, CustomerRepository customers) {
        this.vehicles = vehicles;
        this.customers = customers;
    }

    @Transactional
    public BankVehicleResponse create(BankVehicleRequest request, Authentication auth) {
        BankVehicle vehicle = new BankVehicle();
        apply(vehicle, request, null);
        OffsetDateTime now = OffsetDateTime.now();
        vehicle.setVehicleSts("ACTIVE");
        vehicle.setCrtDt(now);
        vehicle.setCrtBy(actor(auth));
        vehicle.setUpdDt(now);
        vehicle.setUpdBy(actor(auth));
        return save(vehicle);
    }

    @Transactional
    public BankVehicleResponse update(Long id, BankVehicleRequest request, Authentication auth) {
        BankVehicle vehicle = find(id);
        apply(vehicle, request, id);
        vehicle.setUpdDt(OffsetDateTime.now());
        vehicle.setUpdBy(actor(auth));
        return save(vehicle);
    }

    @Transactional
    public BankVehicleResponse changeStatus(Long id, BankVehicleStatusRequest request, Authentication auth) {
        BankVehicle vehicle = find(id);
        vehicle.setVehicleSts(allowed(request.vehicleSts(), STATUSES, "vehicleSts"));
        vehicle.setUpdDt(OffsetDateTime.now());
        vehicle.setUpdBy(actor(auth));
        return save(vehicle);
    }

    @Transactional(readOnly = true)
    public BankVehicleResponse get(Long id) {
        return map(find(id));
    }

    @Transactional(readOnly = true)
    public Page<BankVehicleResponse> search(Long ownerCustId, String registrationNo, String chassisNo, String vehicleSts, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw bad("page must be >= 0 and size between 1 and 100");
        if (ownerCustId != null && ownerCustId <= 0) throw bad("ownerCustId must be positive");
        Specification<BankVehicle> spec = (root, q, cb) -> cb.conjunction();
        if (ownerCustId != null)
            spec = spec.and((root, q, cb) -> cb.equal(root.get("owner").get("custId"), ownerCustId));
        if (notBlank(registrationNo)) {
            String reg = normalize(registrationNo);
            spec = spec.and((root, q, cb) -> cb.equal(cb.upper(root.get("registrationNo")), reg));
        }
        if (notBlank(chassisNo)) {
            String chassis = normalize(chassisNo);
            spec = spec.and((root, q, cb) -> cb.equal(cb.upper(root.get("chassisNo")), chassis));
        }
        if (notBlank(vehicleSts)) {
            String sts = allowed(vehicleSts, STATUSES, "vehicleSts");
            spec = spec.and((root, q, cb) -> cb.equal(root.get("vehicleSts"), sts));
        }
        return vehicles.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "vehicleId"))).map(this::map);
    }

    private void apply(BankVehicle v, BankVehicleRequest r, Long excludeId) {
        Customer owner = customers.findById(r.ownerCustId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        String chassis = requiredUpper(r.chassisNo(), "chassisNo");
        boolean chassisExists = excludeId == null ? vehicles.existsByChassisNoIgnoreCase(chassis) : vehicles.existsByChassisNoIgnoreCaseAndVehicleIdNot(chassis, excludeId);
        if (chassisExists) throw conflict("Chassis number already registered");
        String registration = normalize(r.registrationNo());
        // A native query isn't necessary: rely on the case-insensitive unique index for registration collisions.
        v.setOwner(owner);
        v.setChassisNo(chassis);
        v.setRegistrationNo(registration);
        v.setRegistrationDt(r.registrationDt());
        v.setEngineNo(trim(r.engineNo()));
        v.setVehicleMake(required(r.vehicleMake(), "vehicleMake"));
        v.setVehicleModel(required(r.vehicleModel(), "vehicleModel"));
        v.setVehicleVariant(trim(r.vehicleVariant()));
        v.setManufactureYear(r.manufactureYear());
        v.setVehicleType(allowed(r.vehicleType(), TYPES, "vehicleType"));
        v.setVehicleCondition(allowed(r.vehicleCondition(), CONDITIONS, "vehicleCondition"));
        v.setColour(trim(r.colour()));
        v.setEngineCapacityCc(r.engineCapacityCc());
    }

    private BankVehicleResponse save(BankVehicle v) {
        try {
            return map(vehicles.saveAndFlush(v));
        } catch (DataIntegrityViolationException e) {
            throw conflict("Vehicle chassis or registration number already exists, or invalid vehicle data");
        }
    }

    private BankVehicle find(Long id) {
        if (id == null || id <= 0) throw bad("vehicleId must be positive");
        return vehicles.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
    }

    private BankVehicleResponse map(BankVehicle v) {
        return new BankVehicleResponse(v.getVehicleId(), v.getOwner().getCustId(), v.getOwner().getCustNm(),
                v.getRegistrationNo(), v.getRegistrationDt(), v.getChassisNo(), v.getEngineNo(),
                v.getVehicleMake(), v.getVehicleModel(), v.getVehicleVariant(), v.getManufactureYear(),
                v.getVehicleType(), v.getVehicleCondition(), v.getColour(), v.getEngineCapacityCc(),
                v.getVehicleSts(), v.getCrtDt(), v.getCrtBy(), v.getUpdDt(), v.getUpdBy());
    }

    private static String actor(Authentication a) {
        if (a == null || !a.isAuthenticated() || !notBlank(a.getName()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        String name = a.getName();
        if (name.length() > 100) throw bad("Authenticated username exceeds 100 characters");
        return name;
    }

    private static String required(String s, String field) {
        if (!notBlank(s)) throw bad(field + " is required");
        return s.trim();
    }

    private static String requiredUpper(String s, String field) {
        return required(s, field).toUpperCase(Locale.ROOT);
    }

    private static String allowed(String s, Set<String> values, String field) {
        String normalized = requiredUpper(s, field);
        if (!values.contains(normalized)) throw bad("Invalid " + field + ": " + normalized);
        return normalized;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String trim(String s) {
        return notBlank(s) ? s.trim() : null;
    }

    private static String normalize(String s) {
        String t = trim(s);
        return t == null ? null : t.toUpperCase(Locale.ROOT);
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static ResponseStatusException conflict(String msg) {
        return new ResponseStatusException(HttpStatus.CONFLICT, msg);
    }
}
