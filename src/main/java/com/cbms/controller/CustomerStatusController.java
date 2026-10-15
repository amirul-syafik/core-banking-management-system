package com.cbms.controller;

import com.cbms.dto.CustomerStatusUpdateRequest;
import com.cbms.entity.Customer;
import com.cbms.service.CustomerStatusService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerStatusController {

    private final CustomerStatusService customerStatusService;

    public CustomerStatusController(
            CustomerStatusService customerStatusService) {
        this.customerStatusService = customerStatusService;
    }

    @PatchMapping("/{custId}/status")
    public Customer updateStatus(
            @PathVariable Long custId,
            @Valid @RequestBody CustomerStatusUpdateRequest request) {

        return customerStatusService.updateStatus(custId, request);
    }
}
