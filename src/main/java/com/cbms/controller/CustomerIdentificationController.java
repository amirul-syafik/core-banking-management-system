package com.cbms.controller;

import com.cbms.dto.CustomerIdentificationRequest;
import com.cbms.dto.CustomerIdentificationResponse;
import com.cbms.service.CustomerIdentificationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{custId}/identifications")
public class CustomerIdentificationController {

    private final CustomerIdentificationService identificationService;

    public CustomerIdentificationController(
            CustomerIdentificationService identificationService
    ) {
        this.identificationService = identificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerIdentificationResponse create(
            @PathVariable Long custId,
            @Valid @RequestBody CustomerIdentificationRequest request,
            Authentication authentication
    ) {
        return identificationService.create(
                custId,
                request,
                authentication
        );
    }

    @GetMapping
    public List<CustomerIdentificationResponse> list(
            @PathVariable Long custId
    ) {
        return identificationService.list(custId);
    }

    @GetMapping("/{idnId}")
    public CustomerIdentificationResponse get(
            @PathVariable Long custId,
            @PathVariable Long idnId
    ) {
        return identificationService.get(custId, idnId);
    }

    @PutMapping("/{idnId}")
    public CustomerIdentificationResponse update(@PathVariable Long custId, @PathVariable Long idnId,
                                                 @Valid @RequestBody CustomerIdentificationRequest request, Authentication authentication) {
        return identificationService.update(custId, idnId, request, authentication);
    }
}
