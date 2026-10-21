package com.cbms.controller;

import com.cbms.dto.*;
import com.cbms.service.CustomerStatusApprovalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{custId}/status-requests")
public class CustomerStatusApprovalController {
    private final CustomerStatusApprovalService service;

    public CustomerStatusApprovalController(CustomerStatusApprovalService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomerStatusRequestView> list(@PathVariable Long custId) {
        return service.list(custId);
    }

    @PostMapping
    public CustomerStatusRequestView propose(@PathVariable Long custId,
                                             @Valid @RequestBody CustomerStatusProposal request) {
        return service.propose(custId, request);
    }

    @PostMapping("/{requestId}/decision")
    public CustomerStatusRequestView decide(
            @PathVariable Long custId,
            @PathVariable Long requestId,
            @Valid @RequestBody CustomerStatusDecision request) {

        return service.decide(custId, requestId, request);
    }
}
