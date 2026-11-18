package com.cbms.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Test public validation before the read-only report reaches the database. */
class TransactionExceptionIntegrityServiceTest {
    @Test
    void refusesUnknownExceptionType() {
        TransactionExceptionService service = new TransactionExceptionService(mock(JdbcTemplate.class));
        assertThrows(IllegalArgumentException.class, () -> service.report("SQL_INJECTION", 0, 10));
    }

    @Test
    void refusesOversizedOrNegativePages() {
        TransactionExceptionService service = new TransactionExceptionService(mock(JdbcTemplate.class));
        assertThrows(IllegalArgumentException.class, () -> service.report("ALL", -1, 10));
        assertThrows(IllegalArgumentException.class, () -> service.report("ALL", 0, 101));
    }

    @Test
    void journalExceptionFilterIsReadOnlyAndPaginated() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
                eq("UNBALANCED_JOURNAL"), eq("UNBALANCED_JOURNAL"), eq(10), eq(0)))
                .thenReturn(java.util.List.of());
        var report = new TransactionExceptionService(jdbc).report("UNBALANCED_JOURNAL", 0, 10);
        assertEquals(0L, report.get("totalElements"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
