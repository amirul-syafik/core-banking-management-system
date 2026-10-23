package com.cbms.controller;

import com.cbms.service.TransactionExceptionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reconciliation/exceptions")
public class TransactionExceptionController {
    private final TransactionExceptionService service;

    public TransactionExceptionController(TransactionExceptionService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> report(@RequestParam(defaultValue = "ALL") String type,
                                      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return service.report(type, page, size);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> export(@RequestParam(defaultValue = "ALL") String type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transaction-exceptions.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(service.exportCsv(type));
    }
}
