package com.cbms.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class BankIslamicContractProfileServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BankIslamicContractProfileService service = new BankIslamicContractProfileService(jdbc);

    private Authentication actor(String username) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(username);
        return authentication;
    }

    @Test
    void rejectsConventionalSchemeAndDoesNotPersistContract() {
        when(jdbc.queryForList(anyString(), eq(10L))).thenReturn(List.of(Map.of(
                "prod_id", 3L, "banking_typ", "CONVENTIONAL", "prod_cat", "TERM_DEPOSIT")));
        assertThrows(ResponseStatusException.class, () -> service.create(
                new BankIslamicContractProfileService.DraftRequest(10L, "TAWARRUQ",
                        "Proposed commodity purchase and deferred sale terms"), actor("maker")));
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any());
    }

    @Test
    void checkerCannotApproveOwnProfile() {
        when(jdbc.queryForList(anyString(), eq(44L))).thenReturn(List.of(Map.of(
                "profile_id", 44L, "schm_id", 10L,
                "profile_sts", "SUBMITTED", "crt_by", "maker")));
        assertThrows(ResponseStatusException.class, () -> service.decide(44L,
                new BankIslamicContractProfileService.DecisionRequest("APPROVE", "SH-2026-03", "Reviewed"),
                actor("maker")));
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void checkerCannotApproveWithoutExternalApprovalReference() {
        when(jdbc.queryForList(anyString(), eq(44L))).thenReturn(List.of(Map.of(
                "profile_id", 44L, "schm_id", 10L,
                "profile_sts", "SUBMITTED", "crt_by", "maker")));
        assertThrows(ResponseStatusException.class, () -> service.decide(44L,
                new BankIslamicContractProfileService.DecisionRequest("APPROVE", "", "Reviewed"),
                actor("checker")));
    }

    @Test
    void submissionNotifiesIndependentAdministrators() {
        var notifications = mock(BankApprovalNotificationService.class);
        service.setNotificationService(notifications);
        when(jdbc.queryForList(anyString(), eq(44L))).thenReturn(List.of(Map.of(
                "profile_id", 44L, "contract_ref", "ICR-2026-00000001", "schm_id", 10L, "profile_sts", "DRAFT", "crt_by", "maker")));
        when(jdbc.update(anyString(), eq("maker"), eq(44L))).thenReturn(1);
        service.submit(44L, actor("maker"));
        verify(notifications).notifyRole(eq("ADMIN"), eq("maker"), eq("SCHEMES"),
                eq("ISLAMIC_CONTRACT"), eq(44L), eq("PENDING"), anyString(), contains("ICR-2026-00000001"));
    }

    @Test
    void rejectionNotifiesMakerWithDecision() {
        var notifications = mock(BankApprovalNotificationService.class);
        service.setNotificationService(notifications);
        when(jdbc.queryForList(anyString(), eq(44L))).thenReturn(List.of(Map.of(
                "profile_id", 44L, "contract_ref", "ICR-2026-00000001", "schm_id", 10L, "profile_sts", "SUBMITTED", "crt_by", "maker")));
        when(jdbc.update(anyString(), any(), any(), any(), eq("admin02"), eq(44L))).thenReturn(1);
        service.decide(44L, new BankIslamicContractProfileService.DecisionRequest(
                "REJECT", null, "Incomplete external approval documentation"), actor("admin02"));
        verify(notifications).notifyUser(eq("maker"), eq("SCHEMES"), eq("ISLAMIC_CONTRACT"),
                eq(44L), eq("REJECTED"), anyString(), contains("ICR-2026-00000001"));
    }
}
