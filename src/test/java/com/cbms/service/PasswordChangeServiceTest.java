package com.cbms.service;

import com.cbms.dto.ChangePasswordRequest;
import com.cbms.entity.AppUser;
import com.cbms.repository.AdminUserAuditRepository;
import com.cbms.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordChangeServiceTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AdminUserAuditRepository audits = mock(AdminUserAuditRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final PasswordChangeService service = new PasswordChangeService(users, audits, encoder);

    private AppUser user() {
        AppUser user = new AppUser();
        user.setUserId(7L);
        user.setUsername("maker");
        user.setEnabled(true);
        user.setCredentialVersion(3);
        user.setPasswordHash("old-hash");
        when(users.findByUsernameForPasswordUpdate("maker")).thenReturn(Optional.of(user));
        return user;
    }

    @Test void validPasswordChangeRevokesPreviousTokens() {
        AppUser user = user();
        when(encoder.matches("correct-current", "old-hash")).thenReturn(true);
        when(encoder.encode("different-new-pass")).thenReturn("new-hash");
        service.change("maker", new ChangePasswordRequest("correct-current", "different-new-pass"));
        assertEquals(4, user.getCredentialVersion());
        assertEquals("new-hash", user.getPasswordHash());
        verify(users).saveAndFlush(user);
        verify(audits).save(any());
    }

    @Test void wrongCurrentPasswordDoesNotChangeCredentials() {
        AppUser user = user();
        assertThrows(ResponseStatusException.class, () -> service.change("maker",
                new ChangePasswordRequest("wrong-current", "different-new-pass")));
        assertEquals(3, user.getCredentialVersion());
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(audits);
    }

    @Test void reusingPasswordIsRejected() {
        AppUser user = user();
        when(encoder.matches("correct-current", "old-hash")).thenReturn(true);
        when(encoder.matches("same-password", "old-hash")).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.change("maker",
                new ChangePasswordRequest("correct-current", "same-password")));
        assertEquals(3, user.getCredentialVersion());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void missingAccountCannotChangePassword() {
        assertThrows(ResponseStatusException.class, () -> service.change("unknown",
                new ChangePasswordRequest("correct-current", "different-new-pass")));
        verify(users, never()).saveAndFlush(any());
    }
}
