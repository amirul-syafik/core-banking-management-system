package com.cbms.controller;

import com.cbms.dto.BankSchemeCreateRequest;
import com.cbms.dto.BankSchemeResponse;
import com.cbms.service.BankSchemeService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.cbms.dto.BankSchemeUpdateRequest;
import com.cbms.dto.BankSchemeStatusRequest;
import com.cbms.entity.BankSchemeAudit;

import java.util.List;

@RestController
@RequestMapping("/api/schemes")
public class BankSchemeController {

    private final BankSchemeService service;

    public BankSchemeController(BankSchemeService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankSchemeResponse create(
            @Valid @RequestBody BankSchemeCreateRequest request,
            Authentication authentication
    ) {
        return service.create(request, authentication);
    }

    @GetMapping("/{schmId}")
    public BankSchemeResponse get(@PathVariable Long schmId) {
        return service.get(schmId);
    }

    @GetMapping
    public List<BankSchemeResponse> list(
            @RequestParam(required = false) Long prodId
    ) {
        return service.list(prodId);
    }

    @PutMapping("/{schmId}")
    public BankSchemeResponse update(
            @PathVariable Long schmId,
            @Valid @RequestBody BankSchemeUpdateRequest request,
            Authentication authentication
    ) {
        return service.update(schmId, request, authentication);
    }

    @PatchMapping("/{schmId}/status")
    public BankSchemeResponse changeStatus(
            @PathVariable Long schmId,
            @Valid @RequestBody BankSchemeStatusRequest request,
            Authentication authentication
    ) {
        return service.changeStatus(schmId, request, authentication);
    }

    @GetMapping("/{schmId}/audit")
    public java.util.List<BankSchemeAudit> getAuditHistory(
            @PathVariable Long schmId
    ) {
        return service.getAuditHistory(schmId);
    }
}
