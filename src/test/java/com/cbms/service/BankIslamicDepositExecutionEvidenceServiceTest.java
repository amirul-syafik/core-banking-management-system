package com.cbms.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BankIslamicDepositExecutionEvidenceServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BankIslamicDepositExecutionEvidenceService service = new BankIslamicDepositExecutionEvidenceService(jdbc);
    private final Authentication maker = actor("maker");
    private final Authentication checker = actor("checker");

    private static Authentication actor(String name) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(name);
        return auth;
    }

    private BankIslamicDepositExecutionEvidenceService.Submission tawarruq(String third) {
        return new BankIslamicDepositExecutionEvidenceService.Submission(
            "COM-PURCHASE-2026", "BANK-SALE-2026", third,
            "POLICY-ISLAMIC-001", "LEDGER-MAP-001",
            "References are illustrative evidence only and do not authorize financial posting");
    }

    @Test
    void approvedProfitTermsRequiredBeforeEvidenceRecord() {
        when(jdbc.queryForList(anyString(), eq(7L))).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.create(7L, tawarruq("CUSTOMER-SALE-2026"), maker));
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any(Object[].class));
    }

    @Test
    void tawarruqRequiresThreeDistinctKindsOfReferences() {
        when(jdbc.queryForList(anyString(), eq(7L))).thenReturn(List.of(
                Map.of("fd_id", 7L, "fd_sts", "DRAFT", "terms_id", 12L, "contract_typ", "TAWARRUQ")));
        assertThrows(ResponseStatusException.class, () -> service.create(7L, tawarruq(null), maker));
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any(Object[].class));
    }

    @Test
    void wakalahRejectsUnrelatedThirdEvidence() {
        when(jdbc.queryForList(anyString(), eq(7L))).thenReturn(List.of(
                Map.of("fd_id", 7L, "fd_sts", "DRAFT", "terms_id", 12L, "contract_typ", "WAKALAH")));
        assertThrows(ResponseStatusException.class, () -> service.create(7L, tawarruq("UNRELATED-THIRD-EVIDENCE"), maker));
    }

    @Test
    void makerCannotReviewOwnEvidence() {
        when(jdbc.queryForList(anyString(), eq(15L))).thenReturn(List.of(Map.of(
                "execution_id", 15L, "execution_ref", "IER-2026-ABC123ABC123", "fd_id", 7L,
                "review_sts", "SUBMITTED", "crt_by", "maker", "fd_sts", "DRAFT")));
        assertThrows(ResponseStatusException.class, () -> service.decide(15L,
                new BankIslamicDepositExecutionEvidenceService.Decision("ACCEPT", "REVIEW-001", "Reviewed"), maker));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void checkerMustProvideIndependentReviewReference() {
        when(jdbc.queryForList(anyString(), eq(15L))).thenReturn(List.of(Map.of(
                "execution_id", 15L, "execution_ref", "IER-2026-ABC123ABC123", "fd_id", 7L,
                "review_sts", "SUBMITTED", "crt_by", "maker", "fd_sts", "DRAFT")));
        assertThrows(ResponseStatusException.class, () -> service.decide(15L,
                new BankIslamicDepositExecutionEvidenceService.Decision("ACCEPT", "", "Reviewed"), checker));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void depositMustRemainDraftDuringReview() {
        when(jdbc.queryForList(anyString(), eq(15L))).thenReturn(List.of(Map.of(
                "execution_id", 15L, "execution_ref", "IER-2026-ABC123ABC123", "fd_id", 7L,
                "review_sts", "SUBMITTED", "crt_by", "maker", "fd_sts", "ACTIVE")));
        assertThrows(ResponseStatusException.class, () -> service.decide(15L,
                new BankIslamicDepositExecutionEvidenceService.Decision("ACCEPT", "REVIEW-001", "Reviewed"), checker));
    }
}
