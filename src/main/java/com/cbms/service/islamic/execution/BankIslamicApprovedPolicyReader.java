package com.cbms.service.islamic.execution;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.cbms.service.islamic.execution.IslamicExecutionEvidence.ApprovedPolicy;
import com.cbms.service.islamic.execution.IslamicExecutionEvidence.ContractType;
import com.cbms.service.islamic.execution.IslamicExecutionPorts.BankApprovedPolicyReader;

/** Only independently bank-attested, effective policies may drive read-only preflight. */
@Repository
public class BankIslamicApprovedPolicyReader implements BankApprovedPolicyReader {
    private final JdbcTemplate jdbc;
    public BankIslamicApprovedPolicyReader(JdbcTemplate jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApprovedPolicy> currentPolicy(ContractType contractType, Instant asOf) {
        if (contractType == null || asOf == null) return Optional.empty();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT policy_ref, contract_typ, event_codes, trusted_systems, effective_from, effective_until
            FROM public.bank_islamic_execution_policy
            WHERE contract_typ=? AND policy_sts='APPROVED' AND bank_signoff_verified=TRUE
              AND effective_from <= ? AND (effective_until IS NULL OR effective_until > ?)
            ORDER BY policy_id DESC LIMIT 2
            """, contractType.name(), OffsetDateTime.ofInstant(asOf, ZoneOffset.UTC),
            OffsetDateTime.ofInstant(asOf, ZoneOffset.UTC));
        // Multiple apparently active policies are a governance inconsistency; fail closed.
        if (rows.size() != 1) return Optional.empty();
        Map<String, Object> row = rows.get(0);
        if (!contractType.name().equals(row.get("contract_typ"))) return Optional.empty();
        return Optional.of(new ApprovedPolicy(
                Objects.toString(row.get("policy_ref"), ""), contractType,
                instant(row.get("effective_from")), instant(row.get("effective_until")),
                values(row.get("event_codes")), Set.copyOf(values(row.get("trusted_systems")))));
    }

    static List<String> values(Object raw) {
        String source = Objects.toString(raw, "");
        if (source.isBlank()) return List.of();
        return Arrays.stream(source.split(",", -1)).map(String::trim).collect(Collectors.toList());
    }

    static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        if (value instanceof Timestamp t) return t.toInstant();
        if (value instanceof OffsetDateTime o) return o.toInstant();
        if (value instanceof java.util.Date d) return d.toInstant();
        return OffsetDateTime.parse(value.toString()).toInstant();
    }
}
