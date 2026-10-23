package com.cbms.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ReconciliationInvestigationService {
    private final JdbcTemplate jdbc;

    public ReconciliationInvestigationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Same posted GL 200100 credit/debit calculation as AccountReconciliationRepository.
    // One aggregate per account avoids per-account N+1 reconciliation queries.
    private static final String BASE = """
            WITH ledger AS (
              SELECT e.acct_id,
                     SUM(CASE WHEN e.entry_side = 'CREDIT' THEN e.entry_amt
                              WHEN e.entry_side = 'DEBIT' THEN -e.entry_amt ELSE 0 END) AS calculated
              FROM public.bank_ledger_entry e
              JOIN public.bank_ledger_account g ON g.gl_id = e.gl_id AND g.gl_cd = '200100'
              JOIN public.bank_transaction t ON t.txn_id = e.txn_id AND t.txn_sts = 'POSTED'
              GROUP BY e.acct_id
            ), results AS (
              SELECT a.acct_id, a.acct_no, a.ccy_cd,
                     b.ledger_bal AS stored_balance,
                     COALESCE(l.calculated, 0) AS calculated_balance,
                     b.ledger_bal - COALESCE(l.calculated, 0) AS difference,
                     CASE WHEN b.acct_id IS NULL THEN 'UNAVAILABLE'
                          WHEN b.ledger_bal = COALESCE(l.calculated, 0) THEN 'MATCHED'
                          ELSE 'DISCREPANCY' END AS status
              FROM public.bank_account a
              LEFT JOIN public.bank_account_balance b ON b.acct_id = a.acct_id
              LEFT JOIN ledger l ON l.acct_id = a.acct_id
              WHERE (:unused = :unused)
            )
            """.replace("WHERE (:unused = :unused)", "");

    private static final String FILTER = " WHERE LOWER(acct_no) LIKE ? AND (? = 'ALL' OR status = ?) ";

    private void validate(String accountNo, String status) {
        if (accountNo != null && accountNo.length() > 30) throw new IllegalArgumentException("Account search too long");
        if (!Set.of("ALL", "MATCHED", "DISCREPANCY", "UNAVAILABLE").contains(status))
            throw new IllegalArgumentException("Invalid reconciliation status");
    }

    private String pattern(String accountNo) {
        String s = accountNo == null ? "" : accountNo.trim().toLowerCase(Locale.ROOT);
        return "%" + s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private Map<String, Object> row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("acctId", rs.getLong("acct_id"));
        m.put("acctNo", rs.getString("acct_no"));
        m.put("ccyCd", rs.getString("ccy_cd"));
        m.put("storedBalance", rs.getBigDecimal("stored_balance"));
        m.put("calculatedBalance", rs.getBigDecimal("calculated_balance"));
        m.put("difference", rs.getBigDecimal("difference"));
        m.put("status", rs.getString("status"));
        return m;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> report(String accountNo, String status, int page, int size) {
        validate(accountNo, status);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid page or size");
        String like = pattern(accountNo);
        String filter = FILTER.replace("LIKE ?", "LIKE ? ESCAPE '\\'");
        String query = BASE + " SELECT * FROM results " + filter + " ORDER BY acct_id DESC LIMIT ? OFFSET ?";
        List<Map<String, Object>> rows = jdbc.query(query, (rs, i) -> row(rs), like, status, status, size, page * size);
        String stats = BASE + " SELECT COUNT(*) AS total, " +
                "COUNT(*) FILTER (WHERE status = 'MATCHED') AS matched, " +
                "COUNT(*) FILTER (WHERE status = 'DISCREPANCY') AS discrepancies, " +
                "COUNT(*) FILTER (WHERE status = 'UNAVAILABLE') AS unavailable " +
                "FROM results " + filter;
        Map<String, Object> summary = jdbc.queryForMap(stats, like, status, status);
        long total = ((Number) summary.get("total")).longValue();
        return Map.of("content", rows, "number", page, "size", size,
                "totalElements", total, "totalPages", (total + size - 1) / size, "summary", summary);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> investigate(Long acctId, int page, int size) {
        if (acctId == null || acctId <= 0 || page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid investigation parameters");
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM public.bank_account WHERE acct_id = ?", Integer.class, acctId);
        if (exists == null || exists == 0) throw new IllegalArgumentException("Account not found");
        String sql = """
                SELECT e.entry_id, e.txn_id, t.txn_ref, t.txn_typ, t.txn_sts,
                       t.reversal_of_txn_id, e.entry_side, e.entry_amt, e.crt_dt,
                       CASE WHEN e.entry_side = 'CREDIT' THEN e.entry_amt
                            WHEN e.entry_side = 'DEBIT' THEN -e.entry_amt ELSE 0 END AS signed_amount
                FROM public.bank_ledger_entry e
                JOIN public.bank_ledger_account g ON g.gl_id = e.gl_id AND g.gl_cd = '200100'
                JOIN public.bank_transaction t ON t.txn_id = e.txn_id AND t.txn_sts = 'POSTED'
                WHERE e.acct_id = ? ORDER BY e.entry_id DESC LIMIT ? OFFSET ?
                """;
        List<Map<String, Object>> entries = jdbc.query(sql, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("entryId", rs.getLong("entry_id"));
            m.put("txnId", rs.getLong("txn_id"));
            m.put("reference", rs.getString("txn_ref"));
            m.put("type", rs.getString("txn_typ"));
            m.put("status", rs.getString("txn_sts"));
            long reversal = rs.getLong("reversal_of_txn_id");
            m.put("reversalOfTxnId", rs.wasNull() ? null : reversal);
            m.put("side", rs.getString("entry_side"));
            m.put("amount", rs.getBigDecimal("entry_amt"));
            m.put("signedAmount", rs.getBigDecimal("signed_amount"));
            m.put("dateTime", rs.getObject("crt_dt", java.time.OffsetDateTime.class));
            return m;
        }, acctId, size, page * size);
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM public.bank_ledger_entry e
                JOIN public.bank_ledger_account g ON g.gl_id = e.gl_id AND g.gl_cd = '200100'
                JOIN public.bank_transaction t ON t.txn_id = e.txn_id AND t.txn_sts = 'POSTED'
                WHERE e.acct_id = ?
                """, Long.class, acctId);
        long count = total == null ? 0 : total;
        return Map.of("entries", entries, "number", page, "totalElements", count,
                "totalPages", (count + size - 1) / size);
    }

    private String csvCell(Object value) {
        String s = value == null ? "" : value.toString();
        // Prevent spreadsheet formula execution when opening exported CSV.
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public String exportCsv(String accountNo, String status) {
        validate(accountNo, status);
        String filter = FILTER.replace("LIKE ?", "LIKE ? ESCAPE '\\'");
        String sql = BASE + " SELECT * FROM results " + filter + " ORDER BY acct_id DESC LIMIT 10001";
        List<Map<String, Object>> rows = jdbc.query(sql, (rs, i) -> row(rs), pattern(accountNo), status, status);
        if (rows.size() > 10000) throw new IllegalArgumentException("Export exceeds 10,000 rows. Narrow your filters.");
        StringBuilder csv = new StringBuilder("Account Number,Currency,Stored Balance,Ledger Balance,Difference,Status\r\n");
        for (Map<String, Object> r : rows) {
            for (String key : List.of("acctNo", "ccyCd", "storedBalance", "calculatedBalance", "difference", "status")) {
                if (!key.equals("acctNo")) csv.append(',');
                csv.append(csvCell(r.get(key)));
            }
            csv.append("\r\n");
        }
        return csv.toString();
    }
}
