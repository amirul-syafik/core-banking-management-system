package com.cbms.controller;

import com.cbms.dto.*;
import com.cbms.entity.AdminUserAudit;
import com.cbms.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminUserResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse create(@Valid @RequestBody AdminUserCreateRequest request, Authentication auth) {
        return service.create(request, auth);
    }

    @PutMapping("/{id}/roles")
    public AdminUserResponse roles(@PathVariable Long id, @Valid @RequestBody AdminUserUpdateRequest request, Authentication auth) {
        return service.roles(id, request, auth);
    }

    @PatchMapping("/{id}/enabled")
    public AdminUserResponse enabled(@PathVariable Long id, @RequestParam boolean enabled, Authentication auth) {
        return service.enabled(id, enabled, auth);
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@PathVariable Long id, @Valid @RequestBody AdminPasswordResetRequest request, Authentication auth) {
        service.reset(id, request, auth);
    }

    @GetMapping("/{id}/history")
    public List<AdminUserAudit> history(@PathVariable Long id) {
        return service.history(id);
    }
}
