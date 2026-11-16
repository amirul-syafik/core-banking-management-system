package com.cbms.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BankIslamicDepositGuardTest {
    @Test
    void islamicDepositCannotEnterConventionalPostingPath() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(25L)))
                .thenReturn(List.of("ISLAMIC"));
        assertThrows(ResponseStatusException.class,
                () -> BankIslamicDepositGuard.requireConventional(jdbc, 25L, "accrual"));
    }

    @Test
    void conventionalDepositPassesGuard() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(26L)))
                .thenReturn(List.of("CONVENTIONAL"));
        assertDoesNotThrow(() -> BankIslamicDepositGuard.requireConventional(jdbc, 26L, "placement"));
    }

    @Test
    void missingOrUnknownBankingTypeFailsClosed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(26L)))
                .thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(String.class), eq(27L)))
                .thenReturn(List.of("UNKNOWN"));
        assertThrows(ResponseStatusException.class,
                () -> BankIslamicDepositGuard.requireConventional(jdbc, 26L, "placement"));
        assertThrows(ResponseStatusException.class,
                () -> BankIslamicDepositGuard.requireConventional(jdbc, 27L, "placement"));
    }
}
