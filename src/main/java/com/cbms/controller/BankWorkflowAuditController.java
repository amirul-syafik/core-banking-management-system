package com.cbms.controller;

import com.cbms.dto.BankWorkflowAuditResponse;
import com.cbms.service.BankWorkflowAuditService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/workflows/history")
public class BankWorkflowAuditController {
    private final BankWorkflowAuditService service;

    public BankWorkflowAuditController(BankWorkflowAuditService service) {
        this.service = service;
    }

    @GetMapping
    public Page<BankWorkflowAuditResponse> search(
            @RequestParam(required = false) Long instanceId,
            @RequestParam(required = false) Long workflowId,
            @RequestParam(required = false) String actionCd,
            @RequestParam(required = false) String actionBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromDt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toDt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(instanceId, workflowId, actionCd, actionBy, fromDt, toDt, page, size);
    }
}
