package com.cbms.controller;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Confirms the shared catalog is read-only, paginated, parameterized and limited to active MYR GLs. */
class BankLedgerAccountLookupControllerTest {
    static class FakeJdbc extends JdbcTemplate {
        String countSql;
        String lookupSql;
        Object[] lookupArgs;
        int writes;

        @Override public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            countSql = sql;
            return requiredType.cast(Long.valueOf(23));
        }
        @Override public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
            lookupSql = sql;
            lookupArgs = args;
            return List.of();
        }
        @Override public int update(String sql, Object... args) {
            writes++;
            throw new AssertionError("Catalog lookup must never write to the database");
        }
    }

    @Test void lookupUsesActiveMyrOnlyAndPaginates() {
        FakeJdbc jdbc = new FakeJdbc();
        var result = new BankLedgerAccountLookupController(jdbc).eligible("Cash", 2);
        assertEquals(23, result.totalElements());
        assertEquals(3, result.totalPages());
        assertEquals(2, result.page());
        assertTrue(jdbc.countSql.contains("ccy_cd = 'MYR' AND act_flg = TRUE"));
        assertTrue(jdbc.lookupSql.contains("ORDER BY gl_cd LIMIT ? OFFSET ?"));
        assertEquals("%Cash%", jdbc.lookupArgs[0]);
        assertEquals(10, jdbc.lookupArgs[2]);
        assertEquals(20, jdbc.lookupArgs[3]);
        assertEquals(0, jdbc.writes);
    }
    @Test void searchWildcardsAreEscaped() {
        FakeJdbc jdbc = new FakeJdbc();
        new BankLedgerAccountLookupController(jdbc).eligible("!%_", 0);
        assertEquals("%!!!%!_%", jdbc.lookupArgs[0]);
    }
    @Test void negativePagesAndOverlongSearchAreRejected() {
        BankLedgerAccountLookupController service = new BankLedgerAccountLookupController(new FakeJdbc());
        assertThrows(ResponseStatusException.class, () -> service.eligible("", -1));
        assertThrows(ResponseStatusException.class, () -> service.eligible("a".repeat(81), 0));
    }
}
