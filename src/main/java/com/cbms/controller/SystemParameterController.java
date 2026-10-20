package com.cbms.controller;

import com.cbms.dto.*;
import com.cbms.service.SystemParameterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin/parameters")
public class SystemParameterController {
    private final SystemParameterService service;

    public SystemParameterController(SystemParameterService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return service.list();
    }

    @GetMapping("/{key}")
    public Map<String, Object> get(@PathVariable String key) {
        return service.get(key);
    }

    @GetMapping("/{key}/history")
    public List<Map<String, Object>> history(@PathVariable String key) {
        return service.history(key);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody SystemParameterRequest request, Authentication auth) {
        return service.create(request, auth.getName());
    }

    @PutMapping("/{key}")
    public Map<String, Object> update(@PathVariable String key, @Valid @RequestBody SystemParameterUpdateRequest request, Authentication auth) {
        return service.update(key, request, auth.getName());
    }
}
