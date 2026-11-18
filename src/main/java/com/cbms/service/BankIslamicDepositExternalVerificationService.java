package com.cbms.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cbms.service.islamic.execution.IslamicExecutionEvidence.Assessment;
import com.cbms.service.islamic.execution.IslamicExecutionPreflightService;

/** Read-only status of external execution verification. No event credentials are returned. */
@Service
public class BankIslamicDepositExternalVerificationService {
    private final IslamicExecutionPreflightService preflight;

    public BankIslamicDepositExternalVerificationService(IslamicExecutionPreflightService preflight) {
        this.preflight = preflight;
    }

    public record Blocker(String code, String label, String detail) { }
    public record Report(long fdId, String status, boolean postingEnabled,
                         boolean depositActivationEnabled, List<Blocker> blockers, String message) { }

    @Transactional(readOnly = true)
    public Report inspect(Long fdId) {
        if (fdId == null || fdId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid deposit reference");
        }
        Assessment result = preflight.assess(fdId);
        return new Report(fdId, result.status().name(), result.postingAuthorized(),
                result.depositActivationAuthorized(),
                result.blockers().stream().map(code -> new Blocker(code, label(code), detail(code))).toList(),
                "This check is informational. It cannot activate a deposit, execute a trade, " +
                        "transfer funds or post any Islamic journal.");
    }

    private static String label(String code) {
        if (code.startsWith("DEPOSIT_")) return "Deposit Status";
        if (code.startsWith("CONTRACT_")) return "Contract Approval";
        if (code.startsWith("PROFIT_TERMS_")) return "Profit Terms";
        if (code.startsWith("EVIDENCE_")) return "Execution Evidence";
        if (code.startsWith("TIMELINE_")) return "Execution Timeline";
        if (code.startsWith("BANK_POLICY_")) return "Bank Execution Policy";
        if (code.startsWith("EXTERNAL_REFERENCE_")) return "External Reference Validation";
        if (code.startsWith("EXTERNAL_EVENT_")) return "External Event Confirmation";
        return "External Verification";
    }

    private static String detail(String code) {
        return switch (code) {
            case "INVALID_DEPOSIT_ID" -> "Choose a valid Islamic deposit.";
            case "DEPOSIT_NOT_FOUND" -> "An Islamic contract snapshot was not found for this deposit.";
            case "DEPOSIT_STATE_INVALID" -> "The deposit identity or state is not valid.";
            case "DEPOSIT_NOT_DRAFT" -> "The deposit must remain in DRAFT status.";
            case "CONTRACT_NOT_APPROVED" -> "An approved immutable contract snapshot is required.";
            case "PROFIT_TERMS_NOT_APPROVED" -> "An independently approved profit-terms record is required.";
            case "EVIDENCE_NOT_REVIEWED" -> "The linked documentary execution evidence needs independent review.";
            case "TIMELINE_NOT_REVIEWED" -> "The linked documentary execution timeline needs independent review.";
            case "BANK_POLICY_NOT_APPROVED" -> "A bank-approved external execution policy is not configured.";
            case "BANK_POLICY_INVALID", "BANK_POLICY_NOT_EFFECTIVE", "BANK_POLICY_EVENT_MODEL_INVALID" ->
                    "The bank-approved execution policy is missing, invalid or not in effect.";
            case "EXTERNAL_EVENT_UNVERIFIED", "EXTERNAL_EVENT_MISMATCH", "EXTERNAL_EVENT_IN_FUTURE",
                 "EXTERNAL_EVENT_PREDATES_POLICY", "EXTERNAL_EVENT_OUT_OF_SEQUENCE" ->
                    "Trusted external event verification has not passed.";
            case "EXTERNAL_REFERENCE_REUSED" -> "An external event reference is already associated with another record.";
            case "VERIFICATION_SOURCE_UNAVAILABLE" -> "The verification source is unavailable or returned invalid data.";
            default -> "External contract execution is not independently verified.";
        };
    }
}
