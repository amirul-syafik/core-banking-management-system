package com.cbms.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Read-only anomaly indicators; findings require human investigation, not automatic correction. */
@Service
public class TransactionExceptionService {
    private final JdbcTemplate jdbc;
    public TransactionExceptionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String EXCEPTIONS = """
        WITH findings AS (
            SELECT 'DUPLICATE_IDEMPOTENCY_KEY' AS type, 'HIGH' AS severity,
                   MIN(t.txn_id) AS txn_id, MIN(t.acct_id) AS acct_id,
                   'Idempotency key occurs on ' || COUNT(*) || ' transactions' AS description,
                   t.idempotency_key AS detail
            FROM public.bank_transaction t
            WHERE t.idempotency_key IS NOT NULL
            GROUP BY t.idempotency_key HAVING COUNT(*) > 1
            UNION ALL
            SELECT 'ORPHAN_REVERSAL', 'HIGH', r.txn_id, r.acct_id,
                   'Reversal points to a missing original transaction',
                   'Original transaction ID: ' || r.reversal_of_txn_id
            FROM public.bank_transaction r
            LEFT JOIN public.bank_transaction o ON o.txn_id = r.reversal_of_txn_id
            WHERE r.txn_typ = 'REVERSAL' AND r.reversal_of_txn_id IS NOT NULL AND o.txn_id IS NULL
            UNION ALL
            SELECT 'INVALID_REVERSAL_LINK', 'HIGH', r.txn_id, r.acct_id,
                   'Reversal has no original transaction reference', r.txn_ref
            FROM public.bank_transaction r
            WHERE r.txn_typ = 'REVERSAL' AND r.reversal_of_txn_id IS NULL
            UNION ALL
            SELECT 'MULTIPLE_POSTED_REVERSALS', 'HIGH', MIN(r.txn_id), MIN(r.acct_id),
                   'Original transaction has ' || COUNT(*) || ' posted reversals',
                   'Original transaction ID: ' || r.reversal_of_txn_id
            FROM public.bank_transaction r
            WHERE r.txn_typ = 'REVERSAL' AND r.txn_sts = 'POSTED' AND r.reversal_of_txn_id IS NOT NULL
            GROUP BY r.reversal_of_txn_id HAVING COUNT(*) > 1
            UNION ALL
            SELECT 'NONPOSITIVE_LEDGER_AMOUNT', 'HIGH', e.txn_id, e.acct_id,
                   'Ledger entry amount must be positive', 'Ledger entry ID: ' || e.entry_id
            FROM public.bank_ledger_entry e WHERE e.entry_amt <= 0
        )
        """;

    private void validate(String type, int page, int size) {
        if (!Set.of("ALL", "DUPLICATE_IDEMPOTENCY_KEY", "ORPHAN_REVERSAL", "INVALID_REVERSAL_LINK",
                "MULTIPLE_POSTED_REVERSALS", "NONPOSITIVE_LEDGER_AMOUNT").contains(type))
            throw new IllegalArgumentException("Invalid exception type");
        if (page < 0 || size < 1 || size > 100 || (long)page * size > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid page or size");
    }
    private static final String FILTER = " WHERE (? = 'ALL' OR type = ?) ";
    private Map<String,Object> row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("type", rs.getString("type")); m.put("severity", rs.getString("severity"));
        m.put("txnId", rs.getLong("txn_id"));
        long acct = rs.getLong("acct_id"); m.put("acctId", rs.wasNull() ? null : acct);
        m.put("description", rs.getString("description")); m.put("detail", rs.getString("detail"));
        return m;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String,Object> report(String type, int page, int size) {
        validate(type,page,size);
        String countSql = EXCEPTIONS + " SELECT COUNT(*) FROM findings " + FILTER;
        long total = Objects.requireNonNull(jdbc.queryForObject(countSql, Long.class, type, type));
        String sql = EXCEPTIONS + " SELECT * FROM findings " + FILTER +
                " ORDER BY type, txn_id LIMIT ? OFFSET ?";
        var rows = jdbc.query(sql, (rs,i) -> row(rs), type,type,size,page*size);
        return Map.of("content",rows,"number",page,"size",size,"totalElements",total,
                "totalPages",(total+size-1)/size);
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public String exportCsv(String type) {
        validate(type,0,100);
        long total = Objects.requireNonNull(jdbc.queryForObject(
                EXCEPTIONS + " SELECT COUNT(*) FROM findings " + FILTER, Long.class,type,type));
        if (total > 10000) throw new IllegalArgumentException("Export exceeds 10,000 findings; narrow the type filter");
        var rows = jdbc.query(EXCEPTIONS + " SELECT * FROM findings " + FILTER +
                " ORDER BY type, txn_id LIMIT 10000", (rs,i)->row(rs),type,type);
        StringBuilder out = new StringBuilder("Type,Severity,Transaction ID,Account ID,Description,Detail\r\n");
        for (var r : rows) {
            for (String k : List.of("type","severity","txnId","acctId","description","detail")) {
                if (!k.equals("type")) out.append(',');
                String value = Objects.toString(r.get(k), "");
                // Quote all values and neutralize spreadsheet formula injection.
                if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) value = "'" + value;
                out.append('"').append(value.replace("\"","\"\"")).append('"');
            }
            out.append("\r\n");
        }
        return out.toString();
    }
}
