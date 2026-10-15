package com.cbms.controller;

import com.cbms.dto.CustomerAddressRequest;
import com.cbms.entity.CustomerAddress;
import com.cbms.service.CustomerAddressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{custId}/addresses")
public class CustomerAddressController {

    private final CustomerAddressService addressService;

    public CustomerAddressController(
            CustomerAddressService addressService) {
        this.addressService = addressService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerAddress addAddress(
            @PathVariable Long custId,
            @Valid @RequestBody CustomerAddressRequest request) {

        return addressService.addAddress(custId, request);
    }

    @GetMapping
    public List<CustomerAddress> getAddresses(
            @PathVariable Long custId) {

        return addressService.getAddresses(custId);
    }
}