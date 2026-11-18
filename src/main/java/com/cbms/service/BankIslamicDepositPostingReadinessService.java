package com.cbms.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Non-posting preflight for Islamic deposit processing.
 * Evidence/reference review does NOT authorize profit recognition, activation,
 * settlement or journal posting. Until bank-approved execution/accounting
 * policies are implemented and verified, postingEnabled is always false.
 */
@Service
public class BankIslamicDepositPostingReadinessService {
    private final JdbcTemplate jdbc;

    public BankIslamicDepositPostingReadinessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Check(String code, String label, String status, String detail) {}
    public record Report(Long fdId, String depositStatus, String contractType,
                         boolean postingEnabled, List<Check> checks, String message) {}

    @Transactional(readOnly = true)
    public Report inspect(Long fdId) {
        if (fdId == null || fdId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid deposit reference");
        }
        List<Map<String, Object>> fdRows = jdbc.queryForList("""
                SELECT fd_sts FROM public.bank_fixed_deposit WHERE fd_id = ?
                """, fdId);
        if (fdRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Deposit not found");
        }
        String status = Objects.toString(fdRows.get(0).get("fd_sts"), "UNKNOWN");
        List<Check> checks = new ArrayList<>();
        checks.add(new Check("DEPOSIT_DRAFT", "Deposit remains DRAFT",
                "DRAFT".equals(status) ? "PASSED" : "BLOCKED",
                "DRAFT".equals(status) ? "No funds have been placed by this workflow." :
                        "This evidence workflow only supports DRAFT deposits."));

        List<Map<String, Object>> snapshots = jdbc.queryForList("""
                SELECT profile_id, contract_typ, profit_model
                FROM public.bank_islamic_deposit_contract_snapshot WHERE fd_id = ?
                """, fdId);
        if (snapshots.isEmpty()) {
            checks.add(new Check("CONTRACT_SNAPSHOT", "Approved contract snapshot", "BLOCKED",
                    "No immutable Islamic contract snapshot is linked to this deposit."));
            checks.add(new Check("PROFIT_TERMS", "Approved profit terms", "BLOCKED",
                    "Contract snapshot is required."));
            checks.add(new Check("EXECUTION_EVIDENCE", "Independent evidence review", "BLOCKED",
                    "Approved terms and contract snapshot are required."));
            checks.add(new Check("LEDGER_MAPPING", "Reviewed GL account mapping", "BLOCKED",
                    "Reviewed execution evidence is required first."));
            checks.add(new Check("DOCUMENTARY_SEQUENCE", "Documented event chronology reviewed", "BLOCKED",
                    "Approved contract snapshot and reviewed execution evidence are required."));
            appendExternalControls(checks);
            return new Report(fdId, status, null, false, List.copyOf(checks),
                    "Financial posting is not enabled for Islamic deposits.");
        }
        Map<String, Object> snapshot = snapshots.get(0);
        String type = Objects.toString(snapshot.get("contract_typ"), "");
        checks.add(new Check("CONTRACT_SNAPSHOT", "Approved contract snapshot", "PASSED",
                "Immutable approved " + type + " contract terms are linked to the draft."));

        List<Map<String, Object>> approvedTerms = jdbc.queryForList("""
                SELECT terms_id FROM public.bank_islamic_deposit_profit_terms
                WHERE fd_id = ? AND profile_id = ? AND contract_typ = ?
                  AND profit_model = ? AND terms_sts = 'APPROVED'
                ORDER BY terms_id DESC LIMIT 1
                """, fdId, snapshot.get("profile_id"), type, snapshot.get("profit_model"));
        if (approvedTerms.isEmpty()) {
            checks.add(new Check("PROFIT_TERMS", "Approved profit terms", "PENDING",
                    "Profit terms require independent approval for the linked contract."));
            checks.add(new Check("EXECUTION_EVIDENCE", "Independent evidence review", "PENDING",
                    "Approved profit terms are required first."));
            checks.add(new Check("LEDGER_MAPPING", "Reviewed GL account mapping", "PENDING",
                    "Reviewed execution evidence is required first."));
        } else {
            checks.add(new Check("PROFIT_TERMS", "Approved profit terms", "PASSED",
                    "A matching, independently approved profit-terms record exists."));
            List<Map<String, Object>> reviewed = jdbc.queryForList("""
                    SELECT execution_id FROM public.bank_islamic_deposit_execution_review
                    WHERE fd_id = ? AND terms_id = ? AND contract_typ = ?
                      AND review_sts = 'REVIEWED'
                    ORDER BY execution_id DESC LIMIT 1
                    """, fdId, approvedTerms.get(0).get("terms_id"), type);
            checks.add(new Check("EXECUTION_EVIDENCE", "Independent evidence review",
                    reviewed.isEmpty() ? "PENDING" : "PASSED",
                    reviewed.isEmpty() ? "Matching execution evidence has not been independently reviewed." :
                            "Evidence references have been reviewed, but underlying transactions are not verified."));
            if (reviewed.isEmpty()) {
                checks.add(new Check("LEDGER_MAPPING", "Reviewed GL account mapping", "PENDING",
                        "Independent execution evidence review must be completed first."));
            } else {
                List<Map<String, Object>> mapping = jdbc.queryForList("""
                        SELECT mapping_id FROM public.bank_islamic_deposit_ledger_mapping_review
                        WHERE fd_id = ? AND execution_id = ? AND mapping_sts = 'REVIEWED'
                        ORDER BY mapping_id DESC LIMIT 1
                        """, fdId, reviewed.get(0).get("execution_id"));
                checks.add(new Check("LEDGER_MAPPING", "Reviewed GL account mapping",
                        mapping.isEmpty() ? "PENDING" : "PASSED",
                        mapping.isEmpty() ? "Proposed GL event pairs still require independent review." :
                                "Proposed GL codes have been reviewed; policy authorization and posting remain blocked."));
            }
        }
        List<Map<String, Object>> timeline = jdbc.queryForList("""
                SELECT timeline_id FROM public.bank_islamic_deposit_execution_timeline
                WHERE fd_id = ? AND contract_typ = ? AND review_sts = 'DOCUMENT_REVIEWED'
                ORDER BY timeline_id DESC LIMIT 1
                """, fdId, type);
        checks.add(new Check("DOCUMENTARY_SEQUENCE", "Documented event chronology reviewed",
                timeline.isEmpty() ? "PENDING" : "PASSED",
                timeline.isEmpty() ? "External event timestamps need independent documentary review." :
                        "Chronology/document references reviewed only; actual external execution has not been verified."));
        appendExternalControls(checks);
        return new Report(fdId, status, type, false, List.copyOf(checks),
                "Readiness is informational. Islamic financial execution and GL posting remain disabled.");
    }

    private static void appendExternalControls(List<Check> checks) {
        checks.add(new Check("EXTERNAL_EXECUTION", "Actual contract execution verification", "BLOCKED",
                "Document references alone do not prove commodity trades, mandate execution or settlement."));
        checks.add(new Check("ACCOUNTING_POLICY", "Bank-approved posting rules and accounting engine", "BLOCKED",
                "Policy authorization, event treatment and the double-entry posting engine are not integrated."));
    }
}
