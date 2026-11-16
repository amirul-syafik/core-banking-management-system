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

class BankIslamicDepositExecutionTimelineServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BankIslamicDepositExecutionTimelineService service =
            new BankIslamicDepositExecutionTimelineService(jdbc);

    private Authentication actor(String username) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(username);
        return auth;
    }
    private void tawarruqReady() {
        when(jdbc.queryForList(contains("FOR UPDATE OF fd"), eq(7L)))
                .thenReturn(List.of(Map.of("fd_sts", "DRAFT", "contract_typ", "TAWARRUQ")));
        when(jdbc.queryForList(contains("ORDER BY e.execution_id DESC"), eq(7L), eq("TAWARRUQ")))
                .thenReturn(List.of(Map.of("execution_id", 21L)));
    }
    private BankIslamicDepositExecutionTimelineService.Proposal proposal(String first, String second, String third) {
        return new BankIslamicDepositExecutionTimelineService.Proposal(first, second, third,
                "External documents supplied by UAT maker; chronology only, not proof of execution.");
    }
    @Test
    void tawarruqRequiresOrderedThreeEventsWithoutPosting() {
        tawarruqReady();
        assertThrows(ResponseStatusException.class, () -> service.create(7L, proposal(
                "2026-01-01T11:00:00+08:00", "2026-01-01T10:00:00+08:00",
                "2026-01-01T12:00:00+08:00"), actor("maker")));
        assertThrows(ResponseStatusException.class, () -> service.create(7L, proposal(
                "2026-01-01T09:00:00+08:00", "2026-01-01T10:00:00+08:00",
                null), actor("maker")));
        verify(jdbc, never()).queryForObject(contains("INSERT INTO"), eq(Long.class), any(Object[].class));
    }
    @Test
    void missingReviewedEvidenceBlocksTimeline() {
        when(jdbc.queryForList(contains("FOR UPDATE OF fd"), eq(7L)))
                .thenReturn(List.of(Map.of("fd_sts", "DRAFT", "contract_typ", "TAWARRUQ")));
        when(jdbc.queryForList(contains("ORDER BY e.execution_id DESC"), eq(7L), eq("TAWARRUQ")))
                .thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.create(7L, proposal(
                "2026-01-01T09:00:00+08:00", "2026-01-01T10:00:00+08:00",
                "2026-01-01T11:00:00+08:00"), actor("maker")));
    }
    @Test
    void checkerCannotApproveOwnTimeline() {
        when(jdbc.queryForList(contains("WHERE t.timeline_id"), eq(11L)))
                .thenReturn(List.of(Map.of("fd_sts", "DRAFT", "review_sts", "SUBMITTED",
                        "crt_by", "maker", "evidence_sts", "REVIEWED")));
        assertThrows(ResponseStatusException.class, () -> service.decide(11L,
                new BankIslamicDepositExecutionTimelineService.Decision("ACCEPT", "INDEPENDENT-001", "Reviewed"),
                actor("maker")));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test
    void approvingWithoutReferenceIsRejected() {
        when(jdbc.queryForList(contains("WHERE t.timeline_id"), eq(11L)))
                .thenReturn(List.of(Map.of("fd_sts", "DRAFT", "review_sts", "SUBMITTED",
                        "crt_by", "maker", "evidence_sts", "REVIEWED")));
        assertThrows(ResponseStatusException.class, () -> service.decide(11L,
                new BankIslamicDepositExecutionTimelineService.Decision("ACCEPT", "", "Reviewed"),
                actor("checker")));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
    @Test
    void futureDatedChronologyIsBlocked() {
        tawarruqReady();
        var future = java.time.OffsetDateTime.now().plusDays(1);
        assertThrows(ResponseStatusException.class, () -> service.create(7L,
                proposal(future.toString(), future.plusMinutes(10).toString(),
                        future.plusMinutes(20).toString()), actor("maker")));
    }
}
