package com.cbms;

import com.cbms.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BankFixedDepositCheckerAssignmentTest {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    BankApprovalNotificationService notifier = mock(BankApprovalNotificationService.class);
    BankFixedDepositApprovalService service;

    private static Authentication user(String username, String role) {
        return new UsernamePasswordAuthenticationToken(username, "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @BeforeEach void setup() {
        service = new BankFixedDepositApprovalService(jdbc, new ObjectMapper(),
                mock(BankFixedDepositMaturitySettlementService.class),
                mock(BankFixedDepositRenewalService.class),
                mock(BankFixedDepositPrematureWithdrawalService.class));
        service.setNotificationService(notifier);
    }

    @Test void nonAdminCannotAssignChecker() {
        var error = assertThrows(ResponseStatusException.class,
                () -> service.assign(19L, "CHECKER01", user("MAKER01", "MAKER")));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test void makerCannotBeAssignedToOwnRequest() {
        when(jdbc.queryForList(anyString(), eq(19L))).thenReturn(List.of(
                Map.of("request_id", 19L, "requested_by", "MAKER01", "request_status", "PENDING")));
        var error = assertThrows(ResponseStatusException.class,
                () -> service.assign(19L, "MAKER01", user("ADMIN01", "ADMIN")));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(notifier, never()).notifyUser(anyString(), anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString());
    }

    @Test void disabledOrWrongRoleCheckerIsRejected() {
        when(jdbc.queryForList(anyString(), eq(19L))).thenReturn(List.of(
                Map.of("request_id", 19L, "requested_by", "MAKER01", "request_status", "PENDING")));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("VIEWER01"))).thenReturn(0);
        var error = assertThrows(ResponseStatusException.class,
                () -> service.assign(19L, "VIEWER01", user("ADMIN01", "ADMIN")));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test void assignmentCreatesAuditRecordAndTargetsTheChecker() {
        when(jdbc.queryForList(anyString(), eq(19L))).thenReturn(List.of(
                Map.of("request_id", 19L, "requested_by", "MAKER01", "request_status", "PENDING")));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("CHECKER01"))).thenReturn(1);
        when(jdbc.queryForObject(contains("INSERT INTO public.bank_fd_approval_assignment_history"),
                eq(Long.class), eq(19L), isNull(), eq("CHECKER01"), eq("ADMIN01"))).thenReturn(100L);
        var result = service.assign(19L, "CHECKER01", user("ADMIN01", "ADMIN"));
        assertEquals("CHECKER01", result.get("assignedChecker"));
        verify(jdbc).queryForObject(contains("bank_fd_approval_assignment_history"), eq(Long.class),
                eq(19L), isNull(), eq("CHECKER01"), eq("ADMIN01"));
        verify(notifier).notifyUser(eq("CHECKER01"), eq("FIXED_DEPOSITS"), eq("FD_APPROVAL"),
                eq(19L), eq("ASSIGNED_100"), anyString(), anyString());
        verify(notifier).notifyUser(eq("MAKER01"), eq("FIXED_DEPOSITS"), eq("FD_APPROVAL"),
                eq(19L), eq("CHECKER_ASSIGNED_100"), anyString(), anyString());
    }

    @Test void anotherAdminCannotDecideAssignedRequest() {
        when(jdbc.queryForList(anyString(), eq(19L))).thenReturn(List.of(Map.of(
                "request_id",19L,"fd_id",7L,"action_type","MATURITY_SETTLEMENT",
                "payload","{\"destinationAccountId\":2}","request_status","PENDING",
                "requested_by","MAKER01","assigned_checker","CHECKER01")));
        var error = assertThrows(ResponseStatusException.class,
                () -> service.decide(19L, true, "Reviewed", user("ADMIN02", "ADMIN")));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }
}
