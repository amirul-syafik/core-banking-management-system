package com.cbms.service.islamic.execution;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.cbms.service.islamic.execution.IslamicExecutionPorts.ExternalReferenceRegistry;

/**
 * Read-only conflict detection. DB UNIQUE constraints are the ultimate replay
 * control when the separately approved trusted receipt-ingestion adapter exists.
 * No receipt is created from this service or by the browser.
 */
@Repository
public class BankIslamicExternalReferenceRegistry implements ExternalReferenceRegistry {
    private final JdbcTemplate jdbc;
    public BankIslamicExternalReferenceRegistry(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }

    @Override
    @Transactional(readOnly = true)
    public boolean isLinkedToDifferentDeposit(String externalSystem, String reference, long depositId) {
        if (externalSystem == null || externalSystem.isBlank() ||
            reference == null || reference.isBlank() || depositId <= 0) return true;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT receipt_id FROM public.bank_islamic_external_event_receipt
                WHERE external_system=? AND external_event_ref=? AND fd_id<>? LIMIT 1
                """, externalSystem, reference, depositId);
        return !rows.isEmpty();
    }
}
