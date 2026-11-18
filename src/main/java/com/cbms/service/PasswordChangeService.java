package com.cbms.service;

import com.cbms.dto.ChangePasswordRequest;
import com.cbms.entity.AdminUserAudit;
import com.cbms.entity.AppUser;
import com.cbms.repository.AdminUserAuditRepository;
import com.cbms.repository.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordChangeService {
    private final AppUserRepository users;
    private final AdminUserAuditRepository audits;
    private final PasswordEncoder encoder;

    public PasswordChangeService(AppUserRepository users, AdminUserAuditRepository audits, PasswordEncoder encoder) {
        this.users = users;
        this.audits = audits;
        this.encoder = encoder;
    }

    @Transactional
    public void change(String username, ChangePasswordRequest request) {
        // A database lock serializes concurrent password resets and changes.
        AppUser user = users.findByUsernameForPasswordUpdate(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is not active");
        }
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setCredentialVersion(Math.incrementExact(user.getCredentialVersion()));
        users.saveAndFlush(user);
        audits.save(new AdminUserAudit(user.getUserId(), username, "CHANGE_PASSWORD", "Password changed by user"));
        // Never persist/log plaintext passwords or reset links.
    }
}
