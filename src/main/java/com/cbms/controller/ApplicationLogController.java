package com.cbms.controller;

import com.cbms.service.ApplicationLogService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/logs")
public class ApplicationLogController {
    private final ApplicationLogService service;

    public ApplicationLogController(ApplicationLogService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String level,
                                    @RequestParam(required = false) String logger, @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.search(level, logger, keyword, from, to, page, size);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return service.summary();
    }

    @GetMapping(value = "/download", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> download(@RequestParam(required = false) String level,
                                           @RequestParam(required = false) String logger, @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=cbms-logs.txt")
                .body(service.download(level, logger, keyword, from, to));
    }
}
