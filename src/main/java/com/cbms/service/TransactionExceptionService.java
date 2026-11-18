package com.cbms.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Read-only anomaly indicators; findings require human investigation, not automatic correction.
 */
@Service
public class TransactionExceptionService {
    private final JdbcTemplate jdbc;

    public TransactionExceptionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // These are diagnostic indicators, not automatic repair/posting instructions.
    // Journal-pair invariants below are intentionally limited to ordinary transactions:
    // Islamic contracts and other financial products have different accounting models.
    private static final String EXCEPTIONS = """
            WITH findings AS (
                SELECT 'DUPLICATE_IDEMPOTENCY_KEY' AS type, 'HIGH' AS severity,
                       MIN(t.txn_id) AS txn_id, MIN(t.acct_id) AS acct_id,
                       'More than one transaction uses the same idempotency key' AS description,
                       'Duplicate key detected (value redacted)' AS detail
                FROM public.bank_transaction t
                WHERE t.idempotency_key IS NOT NULL
                GROUP BY t.idempotency_key HAVING COUNT(*) > 1
                UNION ALL
                SELECT 'ORPHAN_REVERSAL', 'HIGH', r.txn_id, r.acct_id,
                       'Reversal points to a missing original transaction',
                       'Original transaction is unavailable'
                FROM public.bank_transaction r
                LEFT JOIN public.bank_transaction o ON o.txn_id = r.reversal_of_txn_id
                WHERE r.txn_typ = 'REVERSAL' AND r.reversal_of_txn_id IS NOT NULL AND o.txn_id IS NULL
                UNION ALL
                SELECT 'INVALID_REVERSAL_LINK', 'HIGH', r.txn_id, r.acct_id,
                       'Reversal has no original transaction reference',
                       'Original transaction is not linked'
                FROM public.bank_transaction r
                WHERE r.txn_typ = 'REVERSAL' AND r.reversal_of_txn_id IS NULL
                UNION ALL
                SELECT 'MULTIPLE_POSTED_REVERSALS', 'HIGH', MIN(r.txn_id), MIN(r.acct_id),
                       'More than one posted reversal targets the same transaction',
                       'Multiple posted reversals detected'
                FROM public.bank_transaction r
                WHERE r.txn_typ = 'REVERSAL' AND r.txn_sts = 'POSTED' AND r.reversal_of_txn_id IS NOT NULL
                GROUP BY r.reversal_of_txn_id HAVING COUNT(*) > 1
                UNION ALL
                SELECT 'NONPOSITIVE_LEDGER_AMOUNT', 'HIGH', e.txn_id, e.acct_id,
                       'Journal entry amount must be positive',
                       'Journal entry amount is zero or negative'
                FROM public.bank_ledger_entry e WHERE e.entry_amt <= 0
                UNION ALL
                SELECT 'INVALID_LEDGER_SIDE', 'HIGH', e.txn_id, e.acct_id,
                       'Journal entry has an unsupported debit/credit side',
                       'Expected DEBIT or CREDIT'
                FROM public.bank_ledger_entry e
                WHERE e.entry_side IS NULL OR e.entry_side NOT IN ('DEBIT', 'CREDIT')
                UNION ALL
                SELECT 'POSTED_WITHOUT_LEDGER', 'CRITICAL', t.txn_id, t.acct_id,
                       'Posted ordinary transaction has no journal entries',
                       'No journal entries are linked to the posted transaction'
                FROM public.bank_transaction t
                WHERE t.txn_sts = 'POSTED'
                  AND t.txn_typ IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'REVERSAL')
                  AND NOT EXISTS (SELECT 1 FROM public.bank_ledger_entry e WHERE e.txn_id = t.txn_id)
                UNION ALL
                SELECT 'INCOMPLETE_LEDGER_PAIR', 'HIGH', t.txn_id, t.acct_id,
                       'Posted ordinary transaction does not have exactly two journal entries',
                       'Journal entry count: ' || COUNT(e.entry_id)::text
                FROM public.bank_transaction t
                JOIN public.bank_ledger_entry e ON e.txn_id = t.txn_id
                WHERE t.txn_sts = 'POSTED'
                  AND t.txn_typ IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'REVERSAL')
                GROUP BY t.txn_id, t.acct_id
                HAVING COUNT(e.entry_id) <> 2
                UNION ALL
                SELECT 'UNBALANCED_JOURNAL', 'CRITICAL', t.txn_id, t.acct_id,
                       'Posted ordinary transaction has unequal debit and credit totals',
                       'Journal totals are not balanced'
                FROM public.bank_transaction t
                JOIN public.bank_ledger_entry e ON e.txn_id = t.txn_id
                WHERE t.txn_sts = 'POSTED'
                  AND t.txn_typ IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'REVERSAL')
                GROUP BY t.txn_id, t.acct_id
                HAVING COALESCE(SUM(CASE WHEN e.entry_side = 'DEBIT' THEN e.entry_amt ELSE 0 END), 0)
                    <> COALESCE(SUM(CASE WHEN e.entry_side = 'CREDIT' THEN e.entry_amt ELSE 0 END), 0)
                UNION ALL
                SELECT 'JOURNAL_AMOUNT_MISMATCH', 'HIGH', t.txn_id, t.acct_id,
                       'Posted ordinary transaction journal totals differ from transaction amount',
                       'Expected two matching entries for the posted transaction amount'
                FROM public.bank_transaction t
                JOIN public.bank_ledger_entry e ON e.txn_id = t.txn_id
                WHERE t.txn_sts = 'POSTED'
                  AND t.txn_typ IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'REVERSAL')
                GROUP BY t.txn_id, t.acct_id, t.txn_amt
                HAVING COUNT(e.entry_id) = 2 AND
                  (COALESCE(SUM(CASE WHEN e.entry_side = 'DEBIT' THEN e.entry_amt ELSE 0 END), 0) <> t.txn_amt
                   OR COALESCE(SUM(CASE WHEN e.entry_side = 'CREDIT' THEN e.entry_amt ELSE 0 END), 0) <> t.txn_amt)
                UNION ALL
                SELECT 'LEDGER_ON_UNPOSTED_TRANSACTION', 'HIGH', t.txn_id, t.acct_id,
                       'Journal entries exist for a transaction not marked POSTED',
                       'Transaction status requires investigation'
                FROM public.bank_transaction t
                JOIN public.bank_ledger_entry e ON e.txn_id = t.txn_id
                WHERE t.txn_sts <> 'POSTED'
                GROUP BY t.txn_id, t.acct_id
                UNION ALL
                SELECT 'ORPHAN_LEDGER_ENTRY', 'HIGH', e.txn_id, e.acct_id,
                       'Journal entry references a missing transaction',
                       'Transaction reference unavailable'
                FROM public.bank_ledger_entry e
                LEFT JOIN public.bank_transaction t ON t.txn_id = e.txn_id
                WHERE t.txn_id IS NULL
            )
            """;

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "ALL", "DUPLICATE_IDEMPOTENCY_KEY", "ORPHAN_REVERSAL", "INVALID_REVERSAL_LINK",
            "MULTIPLE_POSTED_REVERSALS", "NONPOSITIVE_LEDGER_AMOUNT", "INVALID_LEDGER_SIDE",
            "POSTED_WITHOUT_LEDGER", "INCOMPLETE_LEDGER_PAIR", "UNBALANCED_JOURNAL",
            "JOURNAL_AMOUNT_MISMATCH", "LEDGER_ON_UNPOSTED_TRANSACTION", "ORPHAN_LEDGER_ENTRY");

    private void validate(String type, int page, int size) {
        if (type == null || !SUPPORTED_TYPES.contains(type))
            throw new IllegalArgumentException("Invalid exception type");
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid page or size");
    }

    private static final String FILTER = " WHERE (? = 'ALL' OR f.type = ?) ";
    private static final String PROJECTION = """
            SELECT f.type, f.severity, f.txn_id, f.acct_id, f.description, f.detail,
                   t.txn_ref, a.acct_no
            FROM findings f
            LEFT JOIN public.bank_transaction t ON t.txn_id = f.txn_id
            LEFT JOIN public.bank_account a ON a.acct_id = f.acct_id
            """;

    private Map<String, Object> row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", rs.getString("type"));
        m.put("severity", rs.getString("severity"));
        m.put("txnId", rs.getLong("txn_id"));
        long acct = rs.getLong("acct_id");
        m.put("acctId", rs.wasNull() ? null : acct);
        m.put("txnRef", rs.getString("txn_ref"));
        m.put("accountNo", rs.getString("acct_no"));
        m.put("description", rs.getString("description"));
        m.put("detail", rs.getString("detail"));
        return m;
    }

    private record CountedFinding(Map<String, Object> value, long total) { }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 30)
    public Map<String, Object> report(String type, int page, int size) {
        validate(type, page, size);
        // Count within the same findings scan as the page (the previous implementation
        // executed the expensive UNION findings query separately for count and rows).
        // Retain the count-only fallback for a page beyond the last available row.
        String sql = EXCEPTIONS + PROJECTION.replace("SELECT f.type,", "SELECT COUNT(*) OVER() AS total_count, f.type,")
                + FILTER + " ORDER BY f.type, f.txn_id LIMIT ? OFFSET ?";
        List<CountedFinding> found = jdbc.query(sql,
                (rs, i) -> new CountedFinding(row(rs), rs.getLong("total_count")),
                type, type, size, page * size);
        long total = found.isEmpty()
                ? (page == 0 ? 0L : Objects.requireNonNull(jdbc.queryForObject(
                        EXCEPTIONS + " SELECT COUNT(*) FROM findings f " + FILTER,
                        Long.class, type, type)))
                : found.get(0).total();
        List<Map<String, Object>> rows = found.stream().map(CountedFinding::value).toList();
        return Map.of("content", rows, "number", page, "size", size, "totalElements", total,
                "totalPages", (total / size) + (total % size == 0 ? 0 : 1));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 60)
    public String exportCsv(String type) {
        validate(type, 0, 100);
        // One bounded scan instead of a full count followed by a second full scan.
        // The extra row is a sentinel: never return a truncated or misleading CSV.
        var rows = jdbc.query(EXCEPTIONS + PROJECTION + FILTER +
                " ORDER BY f.type, f.txn_id LIMIT 10001", (rs, i) -> row(rs), type, type);
        if (rows.size() > 10000) {
            throw new IllegalArgumentException("Export exceeds 10,000 findings; narrow the type filter");
        }
        StringBuilder out = new StringBuilder("Type,Severity,Transaction Reference,Account Number,Description,Detail\r\n");
        for (var r : rows) {
            for (String k : List.of("type", "severity", "txnRef", "accountNo", "description", "detail")) {
                if (!k.equals("type")) out.append(',');
                String value = Objects.toString(r.get(k), "");
                // Quote all values and neutralize spreadsheet formula injection.
                if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) value = "'" + value;
                out.append('"').append(value.replace("\"", "\"\"")).append('"');
            }
            out.append("\r\n");
        }
        return out.toString();
    }
}
