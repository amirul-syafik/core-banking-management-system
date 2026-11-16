package com.cbms.service;

import com.cbms.dto.AdminPasswordResetRequest;
import com.cbms.entity.AppUser;
import com.cbms.repository.AdminUserAuditRepository;
import com.cbms.repository.AppRoleRepository;
import com.cbms.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminUserPasswordResetRevocationTest {
    @Test void adminResetRotatesGenerationAndResolvesQueue() {
        AppUserRepository users = mock(AppUserRepository.class);
        AdminUserAuditRepository audits = mock(AdminUserAuditRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        PasswordRecoveryService recovery = mock(PasswordRecoveryService.class);
        Authentication admin = mock(Authentication.class);
        AppUser user = new AppUser();
        user.setUserId(8L);
        user.setUsername("maker");
        user.setCredentialVersion(10);
        when(users.findForPasswordUpdate(8L)).thenReturn(Optional.of(user));
        when(encoder.encode("strong-password-value")).thenReturn("hashed");
        when(admin.getName()).thenReturn("admin");

        var service = new AdminUserService(users, mock(AppRoleRepository.class), audits, encoder, recovery);
        service.reset(8L, new AdminPasswordResetRequest("strong-password-value"), admin);
        assertEquals(11, user.getCredentialVersion());
        assertEquals("hashed", user.getPasswordHash());
        verify(users).saveAndFlush(user);
        verify(recovery).resolveAfterAdminReset(8L, "admin");
        verify(audits).save(any());
    }
}
