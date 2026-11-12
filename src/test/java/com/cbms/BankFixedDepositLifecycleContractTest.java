package com.cbms;

import com.cbms.dto.BankFixedDepositPrematureWithdrawalRequest;
import com.cbms.dto.BankFixedDepositRenewalRequest;
import com.cbms.service.BankFixedDepositMaturityProcessingService;
import com.cbms.service.BankFixedDepositMaturityService;
import com.cbms.service.BankFixedDepositPrematureWithdrawalService;
import com.cbms.service.BankFixedDepositRenewalService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Focused FD lifecycle rejection contracts. These are unit tests, NOT proof of
 * database-level atomicity or of completed principal settlement.
 */
class BankFixedDepositLifecycleContractTest {

    private static final long FD_ID = 900101L;

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BankFixedDepositMaturityProcessingService maturity =
            new BankFixedDepositMaturityProcessingService(jdbc);
    private final BankFixedDepositRenewalService renewal =
            new BankFixedDepositRenewalService(jdbc, mock(BankFixedDepositMaturityService.class));
    private final BankFixedDepositPrematureWithdrawalService uplift =
            new BankFixedDepositPrematureWithdrawalService(jdbc);

    private static BankFixedDepositRenewalRequest request(String type) {
        return new BankFixedDepositRenewalRequest(type, 1L, 3, "FD lifecycle test");
    }

    private static Authentication authenticated() {
        return new UsernamePasswordAuthenticationToken("fd-tester", "n/a", List.of());
    }

    private static void status(HttpStatus expected, Runnable call) {
        ResponseStatusException failure = assertThrows(ResponseStatusException.class, call::run);
        assertEquals(expected, failure.getStatusCode());
    }

    @Test
    void maturityRejectsInvalidDepositIdentifierWithoutWrites() {
        status(HttpStatus.BAD_REQUEST, () -> maturity.process(0L, "fd-tester"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void maturityRejectsUnknownDepositWithoutWrites() {
        when(jdbc.queryForList(anyString(), eq(FD_ID))).thenReturn(List.of());
        status(HttpStatus.NOT_FOUND, () -> maturity.process(FD_ID, "fd-tester"));
    }

    @Test
    void maturityRejectsNonActiveDeposit() {
        when(jdbc.queryForList(anyString(), eq(FD_ID)))
                .thenReturn(List.of(Map.of("fd_sts", "MATURED")));
        status(HttpStatus.CONFLICT, () -> maturity.process(FD_ID, "fd-tester"));
    }

    @Test
    void maturityRejectsDepositBeforeContractualMaturity() {
        when(jdbc.queryForList(anyString(), eq(FD_ID))).thenReturn(List.of(Map.of(
                "fd_sts", "ACTIVE",
                "placement_dt", Date.valueOf(LocalDate.now().minusDays(1)),
                "maturity_dt", Date.valueOf(LocalDate.now().plusDays(2))
        )));
        status(HttpStatus.CONFLICT, () -> maturity.process(FD_ID, "fd-tester"));
    }

    @Test
    void renewalRejectsUnsupportedRenewalType() {
        status(HttpStatus.BAD_REQUEST,
                () -> renewal.renew(FD_ID, request("UNSUPPORTED_TYPE"), "fd-tester"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void renewalRejectsNullRequest() {
        status(HttpStatus.BAD_REQUEST, () -> renewal.renew(FD_ID, null, "fd-tester"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void renewalRejectsUnknownDeposit() {
        when(jdbc.queryForList(anyString(), eq(FD_ID))).thenReturn(List.of());
        status(HttpStatus.NOT_FOUND,
                () -> renewal.renew(FD_ID, request("PRINCIPAL_ONLY"), "fd-tester"));
    }

    @Test
    void renewalRejectsOriginalDepositNotYetMatured() {
        when(jdbc.queryForList(anyString(), eq(FD_ID)))
                .thenReturn(List.of(Map.of("fd_sts", "ACTIVE")));
        status(HttpStatus.CONFLICT,
                () -> renewal.renew(FD_ID, request("PRINCIPAL_ONLY"), "fd-tester"));
    }

    @Test
    void upliftRejectsMissingDestinationAccount() {
        status(HttpStatus.BAD_REQUEST,
                () -> uplift.withdraw(FD_ID, new BankFixedDepositPrematureWithdrawalRequest(null, "test"), authenticated()));
        verifyNoInteractions(jdbc);
    }

    @Test
    void upliftRejectsUnauthenticatedCaller() {
        status(HttpStatus.UNAUTHORIZED,
                () -> uplift.withdraw(FD_ID, new BankFixedDepositPrematureWithdrawalRequest(1L, "test"), null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void upliftRejectsUnknownDeposit() {
        when(jdbc.queryForList(anyString(), eq(FD_ID))).thenReturn(List.of());
        status(HttpStatus.NOT_FOUND,
                () -> uplift.withdraw(FD_ID, new BankFixedDepositPrematureWithdrawalRequest(1L, "test"), authenticated()));
    }

    @Test
    void upliftRejectsAlreadyClosedDeposit() {
        when(jdbc.queryForList(anyString(), eq(FD_ID)))
                .thenReturn(List.of(Map.of("fd_sts", "CLOSED")));
        status(HttpStatus.CONFLICT,
                () -> uplift.withdraw(FD_ID, new BankFixedDepositPrematureWithdrawalRequest(1L, "test"), authenticated()));
    }

    @Test
    void upliftRejectsDepositAtOrAfterMaturity() {
        when(jdbc.queryForList(anyString(), eq(FD_ID))).thenReturn(List.of(Map.of(
                "fd_sts", "ACTIVE",
                "placement_dt", Date.valueOf(LocalDate.now().minusMonths(3)),
                "maturity_dt", Date.valueOf(LocalDate.now().minusDays(1))
        )));
        status(HttpStatus.CONFLICT,
                () -> uplift.withdraw(FD_ID, new BankFixedDepositPrematureWithdrawalRequest(1L, "test"), authenticated()));
    }
}
