package com.cbms.controller;

import com.cbms.entity.CustomerAuditHistory;
import com.cbms.service.CustomerAuditHistoryService;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers/{custId}/audit-history")
public class CustomerAuditHistoryController {

    private final CustomerAuditHistoryService auditHistoryService;

    public CustomerAuditHistoryController(
            CustomerAuditHistoryService auditHistoryService) {

        this.auditHistoryService = auditHistoryService;
    }

    @GetMapping
    public Page<CustomerAuditHistory> findByCustomer(
            @PathVariable Long custId,
            @RequestParam(required = false) String actionTyp,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return auditHistoryService.findByCustomer(
                custId,
                actionTyp,
                page,
                size
        );
    }
}
