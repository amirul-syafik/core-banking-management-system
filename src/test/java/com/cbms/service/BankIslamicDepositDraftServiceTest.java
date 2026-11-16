package com.cbms.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BankIslamicDepositDraftServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BankIslamicDepositDraftService service = new BankIslamicDepositDraftService(jdbc);

    private Map<String, Object> approved() {
        return Map.of("profile_id", 9L, "contract_ref", "ICR-2026-00000001", "schm_id", 12L, "prod_id", 3L,
                "contract_typ", "TAWARRUQ", "profit_model", "DEFERRED_SALE",
                "terms_summary", "Commodity sale subject to a separately executed agreement",
                "shariah_approval_ref", "SH-2026-001", "decided_by", "checker",
                "decided_at", Timestamp.from(Instant.parse("2026-11-12T08:00:00Z")));
    }

    @Test
    void draftReadinessRequiresApprovedContract() {
        when(jdbc.queryForList(anyString(), eq(12L))).thenReturn(List.of(Map.of(
                "prod_id", 3L, "prod_cat", "FIXED_DEPOSIT", "banking_typ", "ISLAMIC",
                "schm_sts", "ACTIVE", "prod_sts", "ACTIVE")));
        when(jdbc.queryForList(anyString(), eq(12L), eq(3L)))
                .thenReturn(List.of());
        var readiness = service.readiness(12L);
        assertFalse(readiness.canCreateDraft());
        assertNull(readiness.approvedProfileId());
    }

    @Test
    void draftReadinessExposesApprovedProfileWithoutEnablingPosting() {
        when(jdbc.queryForList(anyString(), eq(12L))).thenReturn(List.of(Map.of(
                "prod_id", 3L, "prod_cat", "TERM_DEPOSIT", "banking_typ", "ISLAMIC",
                "schm_sts", "ACTIVE", "prod_sts", "ACTIVE")));
        when(jdbc.queryForList(anyString(), eq(12L), eq(3L)))
                .thenReturn(List.of(approved()));
        var readiness = service.readiness(12L);
        assertTrue(readiness.canCreateDraft());
        assertEquals(9L, readiness.approvedProfileId());
        assertEquals("ICR-2026-00000001", readiness.contractReference());
        assertTrue(readiness.message().contains("unavailable"));
    }

    @Test
    void cannotCreateSnapshotUsingContractForDifferentScheme() {
        assertThrows(ResponseStatusException.class, () -> service.captureDraft(
                15L, 40L, 3L, approved(), new BigDecimal("6.00"), "maker"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void cannotLockUnapprovedContract() {
        when(jdbc.queryForList(anyString(), eq(12L), eq(3L))).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.lockApprovedProfile(12L, 3L));
    }

    @Test
    void snapshotPersistsContractAndIndicativeRateOnce() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        assertDoesNotThrow(() -> service.captureDraft(15L, 12L, 3L,
                approved(), new BigDecimal("6.00"), "maker"));
        verify(jdbc, times(1)).update(contains("bank_islamic_deposit_contract_snapshot"), any(Object[].class));
    }
}
