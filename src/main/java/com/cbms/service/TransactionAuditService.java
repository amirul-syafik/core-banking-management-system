package com.cbms.service;

import com.cbms.dto.TransactionAuditEventResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.ArrayList;

@Service
public class TransactionAuditService {
    private static final ZoneId AUDIT_TIME_ZONE = ZoneId.of("Asia/Kuala_Lumpur");
    private static final Set<String> ACTIONS = Set.of("POSTED", "REQUESTED", "APPROVED", "REJECTED", "REVERSED");
    private final NamedParameterJdbcTemplate jdbc;

    public TransactionAuditService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Existing persisted banking records are the source of truth. No parallel audit writes or demo events.
    private static final String EVENTS = """
            SELECT 'POST-' || t.txn_id AS event_key, t.txn_ref AS reference,
                   t.acct_id AS account_id, 'POSTED' AS action, t.crt_by AS actor,
                   t.crt_dt AS event_at, 'Transaction posted (' || t.txn_typ || ', ' || t.txn_sts || ')' AS description,
                   CAST(NULL AS varchar) AS related_reference
              FROM bank_transaction t WHERE t.txn_typ <> 'REVERSAL'
            UNION ALL
            SELECT 'REQUEST-' || r.request_id, t.txn_ref, t.acct_id, 'REQUESTED',
                   r.requested_by, r.requested_at, 'Reversal requested: ' || r.reason, CAST(NULL AS varchar)
              FROM bank_transaction_reversal_request r
              JOIN bank_transaction t ON t.txn_id = r.original_txn_id
            UNION ALL
            SELECT 'DECISION-' || r.request_id, t.txn_ref, t.acct_id, r.status,
                   r.decided_by, r.decided_at,
                   CASE WHEN r.status = 'APPROVED' THEN 'Reversal approved' ELSE 'Reversal rejected' END ||
                   CASE WHEN NULLIF(r.decision_reason, '') IS NOT NULL THEN ': ' || r.decision_reason ELSE '' END,
                   CAST(NULL AS varchar)
              FROM bank_transaction_reversal_request r
              JOIN bank_transaction t ON t.txn_id = r.original_txn_id
             WHERE r.status IN ('APPROVED', 'REJECTED') AND r.decided_at IS NOT NULL
            UNION ALL
            SELECT 'REVERSED-' || a.audit_id, t.txn_ref, t.acct_id, 'REVERSED',
                   a.reversed_by, a.reversed_dt, 'Transaction reversed: ' || a.reason, rt.txn_ref
              FROM bank_transaction_reversal_audit a
              JOIN bank_transaction t ON t.txn_id = a.original_txn_id
              JOIN bank_transaction rt ON rt.txn_id = a.reversal_txn_id
            """;

    @Transactional(readOnly = true)
    public Page<TransactionAuditEventResponse> search(Long accountId, String reference, String action,
                                                      String actor, LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("From date must not exceed To date");
        }
        if (page < 0) throw new IllegalArgumentException("Page must not be negative");
        if (size < 1 || size > 50) throw new IllegalArgumentException("Page size must be between 1 and 50");
        if (accountId != null && accountId < 1) throw new IllegalArgumentException("Invalid account");
        if (action != null && !action.isBlank() && !ACTIONS.contains(action.trim().toUpperCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Invalid audit action");
        }
        var pageable = PageRequest.of(page, size);
        var sql = new StringBuilder(" FROM (" + EVENTS + ") e WHERE 1=1");
        var args = new MapSqlParameterSource();
        if (accountId != null) {
            sql.append(" AND e.account_id = :accountId");
            args.addValue("accountId", accountId);
        }
        if (reference != null && !reference.isBlank()) {
            sql.append(" AND LOWER(e.reference) LIKE :reference");
            args.addValue("reference", "%" + reference.trim().toLowerCase(java.util.Locale.ROOT) + "%");
        }
        if (action != null && !action.isBlank()) {
            sql.append(" AND e.action = :action");
            args.addValue("action", action.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (actor != null && !actor.isBlank()) {
            sql.append(" AND LOWER(e.actor) LIKE :actor");
            args.addValue("actor", "%" + actor.trim().toLowerCase(java.util.Locale.ROOT) + "%");
        }
        if (from != null) {
            sql.append(" AND e.event_at >= :from");
            args.addValue("from", from.atStartOfDay(AUDIT_TIME_ZONE).toOffsetDateTime());
        }
        if (to != null) {
            sql.append(" AND e.event_at < :to");
            args.addValue("to", to.plusDays(1).atStartOfDay(AUDIT_TIME_ZONE).toOffsetDateTime());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + sql, args, Long.class);
        args.addValue("limit", pageable.getPageSize());
        args.addValue("offset", pageable.getOffset());
        var rows = jdbc.query("SELECT e.*" + sql + " ORDER BY e.event_at DESC, e.event_key DESC LIMIT :limit OFFSET :offset",
                args, (rs, rowNum) -> new TransactionAuditEventResponse(
                        rs.getString("event_key"), rs.getString("reference"), rs.getLong("account_id"),
                        rs.getString("action"), rs.getString("actor"),
                        rs.getObject("event_at", OffsetDateTime.class), rs.getString("description"),
                        rs.getString("related_reference")));
        return new PageImpl<>(new ArrayList<>(rows), pageable, total == null ? 0 : total);
    }
}
