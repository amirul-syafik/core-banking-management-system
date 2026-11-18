package com.cbms.controller;

import com.cbms.entity.ApplicationActivityAudit;
import com.cbms.service.ApplicationActivityAuditService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/admin/activity-audit")
public class ApplicationActivityAuditController {
    private final ApplicationActivityAuditService service;

    public ApplicationActivityAuditController(ApplicationActivityAuditService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ApplicationActivityAudit> search(@RequestParam(required = false) String actor,
                                                 @RequestParam(required = false) String method, @RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
                                                 @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) String sortBy,
                                                 @RequestParam(required = false) String sortDirection) {
        return service.search(actor, method, status, from, to, page, size, sortBy, sortDirection);
    }

    @GetMapping("/{auditId}")
    public ApplicationActivityAudit get(@PathVariable Long auditId) {
        return service.get(auditId);
    }
}
