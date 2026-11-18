package com.cbms.service.islamic.execution;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.cbms.service.islamic.execution.IslamicExecutionEvidence.ApprovedPolicy;
import com.cbms.service.islamic.execution.IslamicExecutionEvidence.Assessment;
import com.cbms.service.islamic.execution.IslamicExecutionEvidence.AssessmentStatus;
import com.cbms.service.islamic.execution.IslamicExecutionEvidence.DepositSnapshot;
import com.cbms.service.islamic.execution.IslamicExecutionEvidence.VerifiedExternalEvent;
import com.cbms.service.islamic.execution.IslamicExecutionPorts.BankApprovedPolicyReader;
import com.cbms.service.islamic.execution.IslamicExecutionPorts.DepositStateReader;
import com.cbms.service.islamic.execution.IslamicExecutionPorts.ExternalReferenceRegistry;
import com.cbms.service.islamic.execution.IslamicExecutionPorts.TrustedExternalVerificationGateway;

/**
 * Read-only, fail-closed preflight for independently verifiable external contract
 * events. It does not trade, activate deposits, post journals, or mutate records.
 * Documentary reviews are prerequisites, NOT proof of external execution.
 */
public final class IslamicExecutionPreflightService {
    private final DepositStateReader deposits;
    private final BankApprovedPolicyReader policies;
    private final TrustedExternalVerificationGateway gateway;
    private final ExternalReferenceRegistry references;
    private final Clock clock;

    public IslamicExecutionPreflightService(
            DepositStateReader deposits,
            BankApprovedPolicyReader policies,
            TrustedExternalVerificationGateway gateway,
            ExternalReferenceRegistry references,
            Clock clock) {
        this.deposits = Objects.requireNonNull(deposits, "deposits");
        this.policies = Objects.requireNonNull(policies, "policies");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.references = Objects.requireNonNull(references, "references");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Assessment assess(long depositId) {
        if (depositId <= 0) return blocked(depositId, "INVALID_DEPOSIT_ID");
        final Instant now = clock.instant();
        try {
            Optional<DepositSnapshot> maybeDeposit = deposits.findDeposit(depositId);
            if (maybeDeposit.isEmpty()) return blocked(depositId, "DEPOSIT_NOT_FOUND");
            DepositSnapshot deposit = maybeDeposit.get();
            if (deposit.depositId() != depositId || !hasText(deposit.depositReference())) {
                return blocked(depositId, "DEPOSIT_STATE_INVALID");
            }
            if (!"DRAFT".equals(deposit.lifecycleStatus())) return blocked(depositId, "DEPOSIT_NOT_DRAFT");
            if (!deposit.contractApproved()) return blocked(depositId, "CONTRACT_NOT_APPROVED");
            if (!deposit.profitTermsApproved()) return blocked(depositId, "PROFIT_TERMS_NOT_APPROVED");
            if (!deposit.executionEvidenceReviewed()) return blocked(depositId, "EVIDENCE_NOT_REVIEWED");
            if (!deposit.documentaryTimelineReviewed()) return blocked(depositId, "TIMELINE_NOT_REVIEWED");

            Optional<ApprovedPolicy> maybePolicy = policies.currentPolicy(deposit.contractType(), now);
            if (maybePolicy.isEmpty()) return blocked(depositId, "BANK_POLICY_NOT_APPROVED");
            ApprovedPolicy policy = maybePolicy.get();
            if (policy.contractType() != deposit.contractType() || !hasText(policy.approvalReference())) {
                return blocked(depositId, "BANK_POLICY_INVALID");
            }
            if (now.isBefore(policy.effectiveFrom()) ||
                    (policy.effectiveUntil() != null && !now.isBefore(policy.effectiveUntil()))) {
                return blocked(depositId, "BANK_POLICY_NOT_EFFECTIVE");
            }
            List<String> codes = policy.orderedEventCodes();
            if (codes.isEmpty() || policy.trustedExternalSystems().isEmpty() ||
                    codes.stream().anyMatch(c -> !hasText(c)) ||
                    new HashSet<>(codes).size() != codes.size() ||
                    policy.trustedExternalSystems().stream().anyMatch(s -> !hasText(s))) {
                return blocked(depositId, "BANK_POLICY_EVENT_MODEL_INVALID");
            }

            List<VerifiedExternalEvent> verified = new ArrayList<>();
            Set<String> usedExternalIds = new HashSet<>();
            Instant previous = null;
            for (String eventCode : codes) {
                Optional<VerifiedExternalEvent> maybeEvent = gateway.verify(deposit, policy, eventCode);
                if (maybeEvent.isEmpty()) return blocked(depositId, "EXTERNAL_EVENT_UNVERIFIED");
                VerifiedExternalEvent event = maybeEvent.get();
                if (event.depositId() != depositId ||
                        !deposit.depositReference().equals(event.depositReference()) ||
                        event.contractType() != deposit.contractType() ||
                        !eventCode.equals(event.eventCode()) ||
                        !hasText(event.externalEventReference()) ||
                        !hasText(event.externalSystem()) ||
                        !hasText(event.verificationReference()) ||
                        event.occurredAt() == null ||
                        !policy.trustedExternalSystems().contains(event.externalSystem())) {
                    return blocked(depositId, "EXTERNAL_EVENT_MISMATCH");
                }
                if (event.occurredAt().isAfter(now)) return blocked(depositId, "EXTERNAL_EVENT_IN_FUTURE");
                // Policy cannot be backdated to bless earlier unapproved events.
                if (event.occurredAt().isBefore(policy.effectiveFrom())) {
                    return blocked(depositId, "EXTERNAL_EVENT_PREDATES_POLICY");
                }
                if (previous != null && !event.occurredAt().isAfter(previous)) {
                    return blocked(depositId, "EXTERNAL_EVENT_OUT_OF_SEQUENCE");
                }
                String key = event.externalSystem() + "\u0000" + event.externalEventReference();
                if (!usedExternalIds.add(key) || references.isLinkedToDifferentDeposit(
                        event.externalSystem(), event.externalEventReference(), depositId)) {
                    return blocked(depositId, "EXTERNAL_REFERENCE_REUSED");
                }
                verified.add(event);
                previous = event.occurredAt();
            }
            // Non-posting result; separate bank checker and any posting design remain required.
            return new Assessment(depositId, AssessmentStatus.VERIFIED_FOR_INDEPENDENT_REVIEW,
                    List.of(), verified);
        } catch (RuntimeException ex) {
            // Fail closed, without leaking vendor payloads or sensitive references.
            return blocked(depositId, "VERIFICATION_SOURCE_UNAVAILABLE");
        }
    }

    private static Assessment blocked(long depositId, String reason) {
        return new Assessment(depositId, AssessmentStatus.BLOCKED, List.of(reason), List.of());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
