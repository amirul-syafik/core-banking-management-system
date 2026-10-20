package com.cbms.controller;

import com.cbms.dto.CustomerIndividualRequest;
import com.cbms.entity.CustomerIndividual;
import com.cbms.service.CustomerIndividualService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers/{custId}/individual-details")
public class CustomerIndividualController {

    private final CustomerIndividualService individualService;

    public CustomerIndividualController(
            CustomerIndividualService individualService) {
        this.individualService = individualService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerIndividual createIndividualDetails(
            @PathVariable Long custId,
            @Valid @RequestBody CustomerIndividualRequest request) {

        return individualService.createIndividualDetails(custId, request);
    }

    @GetMapping
    public CustomerIndividual getIndividualDetails(
            @PathVariable Long custId) {

        return individualService.getIndividualDetails(custId);
    }

    @PutMapping("")
    public CustomerIndividual update(@PathVariable Long custId, @Valid @RequestBody CustomerIndividualRequest request, org.springframework.security.core.Authentication authentication) {
        return individualService.update(custId, request, authentication);
    }
}
