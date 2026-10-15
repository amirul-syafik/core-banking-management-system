package com.cbms.controller;

import com.cbms.dto.CustomerCorporateRequest;
import com.cbms.entity.CustomerCorporate;
import com.cbms.service.CustomerCorporateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers/{custId}/corporate-details")
public class CustomerCorporateController {

    private final CustomerCorporateService corporateService;

    public CustomerCorporateController(
            CustomerCorporateService corporateService) {
        this.corporateService = corporateService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerCorporate createCorporateDetails(
            @PathVariable Long custId,
            @Valid @RequestBody CustomerCorporateRequest request) {

        return corporateService.createCorporateDetails(custId, request);
    }

    @GetMapping
    public CustomerCorporate getCorporateDetails(
            @PathVariable Long custId) {

        return corporateService.getCorporateDetails(custId);
    }
}
