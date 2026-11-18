package com.cbms.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cbms.service.islamic.execution.IslamicExecutionEvidence.Assessment;
import com.cbms.service.islamic.execution.IslamicExecutionEvidence.AssessmentStatus;
import com.cbms.service.islamic.execution.IslamicExecutionPreflightService;

/** Explicit release gates for bank sign-off. Read-only; never toggles posting. */
@Service
public class BankIslamicExecutionCompletionGateService {
    private final BankIslamicDepositPostingReadinessService readiness;
    private final IslamicExecutionPreflightService preflight;
    private final JdbcTemplate jdbc;

    public BankIslamicExecutionCompletionGateService(BankIslamicDepositPostingReadinessService readiness,
                                                    IslamicExecutionPreflightService preflight,
                                                    JdbcTemplate jdbc) {
        this.readiness = readiness;
        this.preflight = preflight;
        this.jdbc = jdbc;
    }

    public record Gate(String code, String label, String status, String detail) { }
    public record Report(long fdId, boolean releaseReady, boolean postingEnabled,
                         boolean contractExecutionEnabled, List<Gate> gates, String message) { }

    @Transactional(readOnly = true)
    public Report inspect(Long fdId) {
        if (fdId == null || fdId <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid Islamic deposit reference required");
        BankIslamicDepositPostingReadinessService.Report existing = readiness.inspect(fdId);
        if (existing.contractType() == null || existing.contractType().isBlank())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deposit has no Islamic contract snapshot");
        Assessment verification = preflight.assess(fdId);
        List<Gate> gates = new ArrayList<>();
        List<java.util.Map<String, Object>> policy = jdbc.queryForList("""
             SELECT policy_id FROM public.bank_islamic_execution_policy
             WHERE contract_typ = ? AND policy_sts='APPROVED' AND bank_signoff_verified=TRUE
               AND effective_from<=CURRENT_TIMESTAMP
               AND (effective_until IS NULL OR effective_until>CURRENT_TIMESTAMP)
             LIMIT 2
             """, existing.contractType());
        gates.add(new Gate("BANK_EXECUTION_POLICY", "Independently bank-attested execution policy",
                policy.size() == 1 ? "PASSED" : "BLOCKED",
                policy.size() == 1 ? "One effective, independently attested policy is registered." :
                        "No unique bank-attested, effective execution policy is available."));
        gates.add(new Gate("TRUSTED_EXTERNAL_EXECUTION", "Verified external contract events",
                verification.status() == AssessmentStatus.VERIFIED_FOR_INDEPENDENT_REVIEW ? "REVIEW_REQUIRED" : "BLOCKED",
                verification.status() == AssessmentStatus.VERIFIED_FOR_INDEPENDENT_REVIEW ?
                        "Trusted source preflight is available; an independent decision and durable evidence are still required." :
                        "Trusted counterparty event verification has not passed."));
        gates.add(new Gate("INDEPENDENT_EXECUTION_DECISION", "Execution approval and lifecycle controls", "BLOCKED",
                "No live external execution decision or Islamic deposit activation workflow is authorized."));
        gates.add(new Gate("ACCOUNTING_EVENT_RULES", "Bank-approved Islamic event accounting", "BLOCKED",
                "Documentary GL mapping does not establish contract-specific recognition, profit, fee or loss rules."));
        gates.add(new Gate("BALANCED_POSTING", "Balanced, idempotent Islamic journal engine", "BLOCKED",
                "Dry-run controls exist; no approved Islamic posting engine is connected to the bank ledger."));
        gates.add(new Gate("SETTLEMENT", "External settlement and financial payout", "BLOCKED",
                "No independently verified external settlement adapter or financial payout is configured."));
        gates.add(new Gate("REVERSAL", "Financial reversal and recovery", "BLOCKED",
                "No bank-approved reversal accounting or durable once-only reversal workflow is configured."));
        gates.add(new Gate("RECONCILIATION", "Islamic contract-to-GL-to-bank reconciliation", "BLOCKED",
                "No settlement ledger reconciliation and exception-resolution evidence has been signed off."));
        gates.add(new Gate("BANK_UAT_SIGNOFF", "Shariah, accounting, risk and UAT sign-off", "BLOCKED",
                "Institution-specific financial calculation, independent UAT and sign-off remain outstanding."));
        return new Report(fdId, false, false, false, List.copyOf(gates),
                "Phase 10 controls are partly implemented, but real Islamic execution and financial posting are not enabled.");
    }
}
