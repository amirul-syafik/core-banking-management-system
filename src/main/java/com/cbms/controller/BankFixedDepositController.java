package com.cbms.controller;

import com.cbms.dto.BankFixedDepositCreateRequest;
import com.cbms.dto.BankFixedDepositResponse;
import com.cbms.service.BankFixedDepositService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fixed-deposits")
public class BankFixedDepositController {

    private final BankFixedDepositService service;

    public BankFixedDepositController(
            BankFixedDepositService service
    ) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankFixedDepositResponse create(
            @Valid @RequestBody BankFixedDepositCreateRequest request,
            Authentication authentication
    ) {
        return service.create(request, authentication);
    }

    @GetMapping("/{fdId}")
    public BankFixedDepositResponse get(
            @PathVariable Long fdId
    ) {
        return service.get(fdId);
    }

    @GetMapping
    public List<BankFixedDepositResponse> list() {
        return service.list();
    }
}