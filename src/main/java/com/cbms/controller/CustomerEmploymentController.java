package com.cbms.controller;

import com.cbms.dto.CustomerEmploymentRequest;
import com.cbms.entity.CustomerEmployment;
import com.cbms.service.CustomerEmploymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{custId}/employment")
public class CustomerEmploymentController {

    private final CustomerEmploymentService employmentService;

    public CustomerEmploymentController(
            CustomerEmploymentService employmentService) {
        this.employmentService = employmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerEmployment create(
            @PathVariable Long custId,
            @Valid @RequestBody CustomerEmploymentRequest request) {
        return employmentService.create(custId, request);
    }

    @GetMapping
    public List<CustomerEmployment> findByCustomer(
            @PathVariable Long custId) {
        return employmentService.findByCustomer(custId);
    }
}
