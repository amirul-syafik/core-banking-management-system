package com.cbms.controller;

import com.cbms.service.AdminMonitoringService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/monitoring")
public class AdminMonitoringController {
    private final AdminMonitoringService service;

    public AdminMonitoringController(AdminMonitoringService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return service.overview();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return service.health();
    }

    @GetMapping("/resources")
    public Map<String, Object> resources() {
        return service.resources();
    }

    @GetMapping("/database")
    public Map<String, Object> database() {
        return service.database();
    }

    @GetMapping("/alerts")
    public Map<String, Object> alerts() {
        return service.alerts();
    }
}
