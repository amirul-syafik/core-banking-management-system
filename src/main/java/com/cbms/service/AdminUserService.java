package com.cbms.service;

import com.cbms.dto.*;
import com.cbms.entity.*;
import com.cbms.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminUserService {
    private final AppUserRepository users;
    private final AppRoleRepository roles;
    private final AdminUserAuditRepository audits;
    private final PasswordEncoder encoder;

    public AdminUserService(AppUserRepository users, AppRoleRepository roles, AdminUserAuditRepository audits, PasswordEncoder encoder) {
        this.users = users;
        this.roles = roles;
        this.audits = audits;
        this.encoder = encoder;
    }

    private AppUser require(Long id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String actor(Authentication a) {
        return a.getName();
    }

    private AdminUserResponse response(AppUser u) {
        return new AdminUserResponse(u.getUserId(), u.getUsername(), Boolean.TRUE.equals(u.getEnabled()), u.getRoles().stream().map(AppRole::getRoleNm).collect(Collectors.toCollection(TreeSet::new)), u.getCrtDt());
    }

    private Set<AppRole> resolve(Set<String> names) {
        Set<AppRole> found = new HashSet<>();
        for (String name : names) {
            found.add(roles.findByRoleNm(name).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + name)));
        }
        return found;
    }

    private boolean admin(AppUser u) {
        return u.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getRoleNm()));
    }

    private void guardLastAdmin(AppUser target) {
        if (admin(target) && Boolean.TRUE.equals(target.getEnabled()) && users.findAll().stream().filter(u -> Boolean.TRUE.equals(u.getEnabled()) && admin(u)).count() <= 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove last enabled ADMIN");
    }

    private void audit(AppUser u, Authentication a, String action, String details) {
        audits.save(new AdminUserAudit(u.getUserId(), actor(a), action, details));
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list() {
        return users.findAll().stream().sorted(Comparator.comparing(AppUser::getUserId)).map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(Long id) {
        return response(require(id));
    }

    @Transactional
    public AdminUserResponse create(AdminUserCreateRequest req, Authentication a) {
        String username = req.username().trim();
        if (users.findByUsername(username).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setEnabled(true);
        u.setCrtDt(OffsetDateTime.now());
        u.setRoles(resolve(req.roles()));
        users.saveAndFlush(u);
        audit(u, a, "CREATE", "roles=" + req.roles());
        return response(u);
    }

    @Transactional
    public AdminUserResponse roles(Long id, AdminUserUpdateRequest req, Authentication a) {
        AppUser u = require(id);
        Set<AppRole> next = resolve(req.roles());
        boolean nextAdmin = next.stream().anyMatch(r -> "ADMIN".equals(r.getRoleNm()));
        if (u.getUsername().equals(actor(a)) && !nextAdmin)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove own ADMIN role");
        if (admin(u) && !nextAdmin) guardLastAdmin(u);
        u.setRoles(next);
        users.save(u);
        audit(u, a, "UPDATE_ROLES", "roles=" + req.roles());
        return response(u);
    }

    @Transactional
    public AdminUserResponse enabled(Long id, boolean enabled, Authentication a) {
        AppUser u = require(id);
        if (!enabled && u.getUsername().equals(actor(a)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot disable own account");
        if (!enabled) guardLastAdmin(u);
        u.setEnabled(enabled);
        users.save(u);
        audit(u, a, enabled ? "ENABLE" : "DISABLE", null);
        return response(u);
    }

    @Transactional
    public void reset(Long id, AdminPasswordResetRequest req, Authentication a) {
        AppUser u = require(id);
        u.setPasswordHash(encoder.encode(req.newPassword()));
        users.save(u);
        audit(u, a, "RESET_PASSWORD", "Password reset by administrator");
    }

    @Transactional(readOnly = true)
    public List<AdminUserAudit> history(Long id) {
        require(id);
        return audits.findByTargetUserIdOrderByCreatedAtDesc(id);
    }
}
