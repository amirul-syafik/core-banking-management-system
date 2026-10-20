package com.cbms.controller;

import com.cbms.dto.CustomerOverviewResponse;
import com.cbms.service.CustomerOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerOverviewController {
    private final CustomerOverviewService service;

    public CustomerOverviewController(CustomerOverviewService service) {
        this.service = service;
    }

    @GetMapping("/{custId}/overview")
    public CustomerOverviewResponse overview(@PathVariable Long custId) {
        return service.getOverview(custId);
    }
}
