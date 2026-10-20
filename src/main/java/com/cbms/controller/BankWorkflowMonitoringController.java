package com.cbms.controller;

import com.cbms.dto.*;
import com.cbms.service.BankWorkflowMonitoringService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows/monitoring")
public class BankWorkflowMonitoringController {
    private final BankWorkflowMonitoringService service;

    public BankWorkflowMonitoringController(BankWorkflowMonitoringService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public BankWorkflowMonitoringSummaryResponse summary(@RequestParam(defaultValue = "24") int overdueHours) {
        return service.summary(overdueHours);
    }

    @GetMapping("/pending")
    public BankWorkflowMonitoringTaskPageResponse pending(
            @RequestParam(required = false) String assignedRole,
            @RequestParam(defaultValue = "24") int overdueHours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.pending(assignedRole, overdueHours, page, size);
    }

    @GetMapping("/overdue")
    public BankWorkflowMonitoringTaskPageResponse overdue(
            @RequestParam(required = false) String assignedRole,
            @RequestParam(defaultValue = "24") int overdueHours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.overdue(assignedRole, overdueHours, page, size);
    }

    @GetMapping("/statistics")
    public BankWorkflowMonitoringStatisticsResponse statistics() {
        return service.statistics();
    }
}
