package com.cbms.controller;

import com.cbms.dto.CorporateAuthorizedPersonRequest;
import com.cbms.entity.CorporateAuthorizedPerson;
import com.cbms.service.CorporateAuthorizedPersonService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{corpCustId}/authorized-persons")
public class CorporateAuthorizedPersonController {

    private final CorporateAuthorizedPersonService service;

    public CorporateAuthorizedPersonController(
            CorporateAuthorizedPersonService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CorporateAuthorizedPerson create(
            @PathVariable Long corpCustId,
            @Valid @RequestBody CorporateAuthorizedPersonRequest request) {
        return service.create(corpCustId, request);
    }

    @GetMapping
    public List<CorporateAuthorizedPerson> findAll(
            @PathVariable Long corpCustId) {
        return service.findByCorporateCustomer(corpCustId);
    }
}
