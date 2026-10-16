package com.cbms.controller;

import com.cbms.dto.BankProductCreateRequest;
import com.cbms.dto.BankProductResponse;
import com.cbms.service.BankProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.cbms.dto.BankProductUpdateRequest;
import com.cbms.dto.BankProductStatusRequest;
import com.cbms.entity.BankProductAudit;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class BankProductController {

    private final BankProductService service;

    public BankProductController(BankProductService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankProductResponse create(
            @Valid @RequestBody BankProductCreateRequest request,
            Authentication authentication
    ) {
        return service.create(request, authentication);
    }

    @GetMapping("/{prodId}")
    public BankProductResponse get(@PathVariable Long prodId) {
        return service.get(prodId);
    }

    @GetMapping
    public List<BankProductResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status
    ) {
        return service.list(category, status);
    }

    @PutMapping("/{prodId}")
    public BankProductResponse update(
            @PathVariable Long prodId,
            @Valid @RequestBody BankProductUpdateRequest request,
            Authentication authentication
    ) {
        return service.update(prodId, request, authentication);
    }

    @PatchMapping("/{prodId}/status")
    public BankProductResponse changeStatus(
            @PathVariable Long prodId,
            @Valid @RequestBody BankProductStatusRequest request,
            Authentication authentication
    ) {
        return service.changeStatus(prodId, request, authentication);
    }

    @GetMapping("/{prodId}/audit")
    public java.util.List<BankProductAudit> getAuditHistory(
            @PathVariable Long prodId
    ) {
        return service.getAuditHistory(prodId);
    }
}
