package com.cbms.controller;

import com.cbms.dto.CustomerRelationshipRequest;
import com.cbms.entity.CustomerRelationship;
import com.cbms.service.CustomerRelationshipService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{custId}/relationships")
public class CustomerRelationshipController {

    private final CustomerRelationshipService relationshipService;

    public CustomerRelationshipController(
            CustomerRelationshipService relationshipService) {
        this.relationshipService = relationshipService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerRelationship createRelationship(
            @PathVariable Long custId,
            @Valid @RequestBody CustomerRelationshipRequest request) {

        return relationshipService.createRelationship(custId, request);
    }

    @GetMapping
    public List<CustomerRelationship> getRelationships(
            @PathVariable Long custId) {

        return relationshipService.getRelationships(custId);
    }
}