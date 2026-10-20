package com.cbms.controller;

import com.cbms.dto.OperationalReportPage;
import com.cbms.service.OperationalReportingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class OperationalReportingController {
    private final OperationalReportingService service;

    public OperationalReportingController(OperationalReportingService service) {
        this.service = service;
    }

    @GetMapping("/{type}")
    public OperationalReportPage getReport(
            @PathVariable String type,
            @RequestParam(required = false) Long custId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.report(type, custId, status, currency, fromDate, toDate, page, size);
    }
}
