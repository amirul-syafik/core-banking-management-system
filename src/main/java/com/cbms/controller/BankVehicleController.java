package com.cbms.controller;

import com.cbms.dto.*;
import com.cbms.service.BankVehicleService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vehicles")
public class BankVehicleController {
    private final BankVehicleService service;

    public BankVehicleController(BankVehicleService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BankVehicleResponse> create(@Valid @RequestBody BankVehicleRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, auth));
    }

    @GetMapping("/{vehicleId}")
    public BankVehicleResponse get(@PathVariable Long vehicleId) {
        return service.get(vehicleId);
    }

    @GetMapping
    public Page<BankVehicleResponse> search(@RequestParam(required = false) Long ownerCustId,
                                            @RequestParam(required = false) String registrationNo,
                                            @RequestParam(required = false) String chassisNo,
                                            @RequestParam(required = false) String vehicleSts,
                                            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.search(ownerCustId, registrationNo, chassisNo, vehicleSts, page, size);
    }

    @GetMapping("/customer/{custId}")
    public Page<BankVehicleResponse> byCustomer(@PathVariable Long custId,
                                                @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.search(custId, null, null, null, page, size);
    }

    @PutMapping("/{vehicleId}")
    public BankVehicleResponse update(@PathVariable Long vehicleId, @Valid @RequestBody BankVehicleRequest request, Authentication auth) {
        return service.update(vehicleId, request, auth);
    }

    @PatchMapping("/{vehicleId}/status")
    public BankVehicleResponse status(@PathVariable Long vehicleId, @Valid @RequestBody BankVehicleStatusRequest request, Authentication auth) {
        return service.changeStatus(vehicleId, request, auth);
    }
}
