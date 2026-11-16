package com.cbms.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Proves that large read-only diagnostic queries are not duplicated on normal pages. */
class TransactionExceptionPerformanceTest {
    @Test
    @SuppressWarnings("unchecked")
    void populatedPageUsesSingleFindingsQueryAndWindowCount() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("ALL"), eq("ALL"), eq(10), eq(0)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    assertTrue(sql.contains("COUNT(*) OVER() AS total_count"));
                    RowMapper<?> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("type")).thenReturn("UNBALANCED_JOURNAL");
                    when(rs.getString("severity")).thenReturn("CRITICAL");
                    when(rs.getLong("txn_id")).thenReturn(12L);
                    when(rs.getLong("total_count")).thenReturn(27L);
                    when(rs.wasNull()).thenReturn(true);
                    return List.of(mapper.mapRow(rs, 0));
                });
        Map<String, Object> report = new TransactionExceptionService(jdbc).report("ALL", 0, 10);
        assertEquals(27L, report.get("totalElements"));
        assertEquals(3L, report.get("totalPages"));
        assertEquals(1, ((List<?>) report.get("content")).size());
        verify(jdbc, times(1)).query(anyString(), any(RowMapper.class), eq("ALL"), eq("ALL"), eq(10), eq(0));
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void outOfRangePageFallsBackToAccurateCount() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("ALL"), eq("ALL"), eq(10), eq(100)))
                .thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("ALL"), eq("ALL"))).thenReturn(27L);
        Map<String, Object> report = new TransactionExceptionService(jdbc).report("ALL", 10, 10);
        assertEquals(27L, report.get("totalElements"));
        assertEquals(3L, report.get("totalPages"));
        assertTrue(((List<?>) report.get("content")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void csvRejectsMoreThanTenThousandWithoutSeparateCount() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("ALL"), eq("ALL")))
                .thenAnswer(inv -> {
                    assertTrue(((String) inv.getArgument(0)).contains("LIMIT 10001"));
                    return java.util.Collections.nCopies(10001, Map.of());
                });
        assertThrows(IllegalArgumentException.class, () -> new TransactionExceptionService(jdbc).exportCsv("ALL"));
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any(), any());
    }
}
