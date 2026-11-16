package com.cbms.exception;

import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class ErrorDiagnosticsTest {
    @Test
    void extractsOnlyAllowlistedDatabaseIdentifiers() {
        String sensitive = "Key (cust_id)=(1234) already exists";
        SQLException sql = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"bank_account_uniq\"\nDETAIL: " + sensitive,
                "23505");
        var meta = ErrorDiagnostics.databaseMetadata(new IllegalStateException("wrapper", sql));
        assertEquals("23505", meta.sqlState());
        assertEquals("bank_account_uniq", meta.constraint());
        assertFalse(meta.toString().contains(sensitive));
    }

    @Test
    void ignoresUntrustedStateAndConstraintText() {
        SQLException sql = new SQLException("violates constraint \"unsafe-name;value\"", "23505\nsecret");
        var meta = ErrorDiagnostics.databaseMetadata(sql);
        assertEquals("unknown", meta.sqlState());
        assertEquals("unknown", meta.constraint());
    }

    @Test
    void missingSqlExceptionReturnsUnknownMetadata() {
        var meta = ErrorDiagnostics.databaseMetadata(new IllegalArgumentException("Sensitive customer payload"));
        assertEquals("unknown", meta.sqlState());
        assertEquals("unknown", meta.constraint());
    }

    @Test
    void onlyMappedRoutePatternsAreAllowed() {
        assertEquals("/api/accounts/{acctId}/deposits", ErrorDiagnostics.safeRoute("/api/accounts/{acctId}/deposits"));
        assertEquals("/api/[unmapped]", ErrorDiagnostics.safeRoute("/api/customers/1234?password=abc"));
        assertEquals("/api/[unmapped]", ErrorDiagnostics.safeRoute(null));
    }
}
