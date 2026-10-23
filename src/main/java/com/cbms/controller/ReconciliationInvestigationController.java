package com.cbms.controller;

import com.cbms.service.ReconciliationInvestigationService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

import java.util.*;

@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationInvestigationController {
    private final ReconciliationInvestigationService service;

    public ReconciliationInvestigationController(ReconciliationInvestigationService service) {
        this.service = service;
    }

    @GetMapping("/report")
    public Map<String, Object> report(@RequestParam(defaultValue = "") String accountNo,
                                      @RequestParam(defaultValue = "ALL") String status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return service.report(accountNo, status, page, size);
    }

    @GetMapping("/investigation/{acctId}")
    public Map<String, Object> investigate(@PathVariable Long acctId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.investigate(acctId, page, size);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> export(@RequestParam(defaultValue = "") String accountNo,
                                         @RequestParam(defaultValue = "ALL") String status) {
        String csv = service.exportCsv(accountNo, status);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transaction-reconciliation.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }
}
