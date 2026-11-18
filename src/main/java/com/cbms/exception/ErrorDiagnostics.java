package com.cbms.exception;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Safe, bounded error metadata. Never return SQL text, parameters or database DETAIL lines. */
public final class ErrorDiagnostics {
    private static final Pattern SQL_STATE = Pattern.compile("[A-Za-z0-9]{5}");
    private static final Pattern CONSTRAINT = Pattern.compile("(?i)\\bconstraint \"([A-Za-z_][A-Za-z0-9_]{0,79})\"");
    private static final Pattern ROUTE = Pattern.compile("/[A-Za-z0-9_{}*.-]+(?:/[A-Za-z0-9_{}*.-]+)*");

    private ErrorDiagnostics() { }

    public record DatabaseMetadata(String sqlState, String constraint) { }

    public static DatabaseMetadata databaseMetadata(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = error;
        for (int depth = 0; current != null && depth < 32 && seen.add(current); depth++, current = current.getCause()) {
            if (current instanceof SQLException sql) {
                String state = sql.getSQLState();
                String safeState = state != null && SQL_STATE.matcher(state).matches() ? state.toUpperCase() : "unknown";
                String constraint = "unknown";
                // Strictly extract the identifier alone. Do NOT log the PostgreSQL error text,
                // which can include raw keys, parameter values, or financial/customer data.
                String text = sql.getMessage();
                if (text != null) {
                    Matcher m = CONSTRAINT.matcher(text);
                    if (m.find()) constraint = m.group(1);
                }
                return new DatabaseMetadata(safeState, constraint);
            }
        }
        return new DatabaseMetadata("unknown", "unknown");
    }

    public static String safeRoute(Object mappedPattern) {
        if (!(mappedPattern instanceof String value) || value.length() > 240 || !ROUTE.matcher(value).matches()) {
            return "/api/[unmapped]";
        }
        return value;
    }
}
