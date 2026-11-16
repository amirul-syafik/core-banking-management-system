package com.cbms.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BankIslamicDepositProfitTermsServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BankIslamicDepositProfitTermsService service = new BankIslamicDepositProfitTermsService(jdbc);
    private final Authentication maker = actor("maker");
    private final Authentication checker = actor("checker");

    private static Authentication actor(String username) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(username);
        return auth;
    }

    private void draft(String type, String model) {
        when(jdbc.queryForList(anyString(), eq(7L))).thenReturn(List.of(Map.of(
                "fd_id", 7L, "fd_sts", "DRAFT", "principal_amt", new BigDecimal("10000.00"),
                "profile_id", 11L, "contract_typ", type, "profit_model", model)));
        when(jdbc.queryForList(anyString(), eq(99L))).thenReturn(List.of(Map.of(
                "terms_id", 99L, "fd_id", 7L, "terms_sts", "DRAFT", "crt_by", "maker", "fd_sts", "DRAFT")));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(99L);
    }

    @Test
    void tawarruqAcceptsExplicitProfitButNotAnInterestRate() {
        draft("TAWARRUQ", "DEFERRED_SALE");
        assertThrows(ResponseStatusException.class, () -> service.create(7L,
                new BankIslamicDepositProfitTermsService.Proposal(new BigDecimal("120.00"),
                        new BigDecimal("6.00"), null, "PR-2026-001", "Audited contract terms with external pricing evidence"), maker));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void wakalahRejectsGuaranteedProfitAmount() {
        draft("WAKALAH", "AGENCY");
        assertThrows(ResponseStatusException.class, () -> service.create(7L,
                new BankIslamicDepositProfitTermsService.Proposal(new BigDecimal("100"),
                        new BigDecimal("5.0"), null, "PR-2026-002", "Expected investment rate only; actual profit is variable"), maker));
    }

    @Test
    void mudarabahRequiresProfitSharingRatio() {
        draft("MUDARABAH", "PROFIT_SHARING");
        assertThrows(ResponseStatusException.class, () -> service.create(7L,
                new BankIslamicDepositProfitTermsService.Proposal(null, null,
                        new BigDecimal("125"), "PR-2026-003", "Agreed customer share of realized distributable profit"), maker));
    }

    @Test
    void cannotCreateProfitTermsWithoutAnApprovedContractSnapshot() {
        when(jdbc.queryForList(anyString(), eq(7L))).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.create(7L,
                new BankIslamicDepositProfitTermsService.Proposal(new BigDecimal("120.00"), null,
                        null, "PR-2026-001", "Contract terms with original evidence documentation"), maker));
    }

    @Test
    void makerCannotDecideTheirOwnTerms() {
        when(jdbc.queryForList(anyString(), eq(99L))).thenReturn(List.of(Map.of(
                "terms_id", 99L, "fd_id", 7L, "terms_sts", "SUBMITTED", "crt_by", "maker", "fd_sts", "DRAFT")));
        assertThrows(ResponseStatusException.class, () -> service.decide(99L,
                new BankIslamicDepositProfitTermsService.Decision("APPROVE", "SH-2026-001", "Reviewed"), maker));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void checkerMustProvideApprovalDocumentReference() {
        when(jdbc.queryForList(anyString(), eq(99L))).thenReturn(List.of(Map.of(
                "terms_id", 99L, "fd_id", 7L, "terms_sts", "SUBMITTED", "crt_by", "maker", "fd_sts", "DRAFT")));
        assertThrows(ResponseStatusException.class, () -> service.decide(99L,
                new BankIslamicDepositProfitTermsService.Decision("APPROVE", "", "Reviewed"), checker));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
