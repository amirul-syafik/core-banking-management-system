package com.cbms.controller;

import com.cbms.service.BankFixedDepositMonitoringService;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fixed-deposits/monitoring")
public class BankFixedDepositMonitoringController {

    private final BankFixedDepositMonitoringService service;

    public BankFixedDepositMonitoringController(
            BankFixedDepositMonitoringService service
    ) {
        this.service = service;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return service.summary();
    }

    @GetMapping("/upcoming-maturities")
    public List<Map<String, Object>> upcomingMaturities(
            @RequestParam(defaultValue = "30") int days
    ) {
        return service.upcomingMaturities(days);
    }

    @GetMapping("/overdue-maturities")
    public List<Map<String, Object>> overdueMaturities() {
        return service.overdueMaturities();
    }

    @GetMapping("/missing-accruals")
    public List<Map<String, Object>> missingAccruals() {
        return service.missingAccruals();
    }

    @GetMapping("/closures")
    public List<Map<String, Object>> closureHistory() {
        return service.closureHistory();
    }
}
