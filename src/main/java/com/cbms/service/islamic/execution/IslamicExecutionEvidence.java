package com.cbms.service.islamic.execution;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Non-posting execution-verification contracts. Objects returned by the ports must
 * come from trusted server-side sources, never directly from a client request.
 */
public final class IslamicExecutionEvidence {
    private IslamicExecutionEvidence() { }

    public enum ContractType { TAWARRUQ, WAKALAH, MUDARABAH }
    public enum AssessmentStatus { BLOCKED, VERIFIED_FOR_INDEPENDENT_REVIEW }

    /** Status flags are projections from the database, not submitted form values. */
    public record DepositSnapshot(
            long depositId,
            String depositReference,
            ContractType contractType,
            String lifecycleStatus,
            boolean contractApproved,
            boolean profitTermsApproved,
            boolean executionEvidenceReviewed,
            boolean documentaryTimelineReviewed) {
        public DepositSnapshot {
            if (depositId <= 0) throw new IllegalArgumentException("Invalid deposit ID");
            Objects.requireNonNull(contractType, "contractType");
        }
    }

    /** Must be supplied by the bank-controlled, approved-policy repository. */
    public record ApprovedPolicy(
            String approvalReference,
            ContractType contractType,
            Instant effectiveFrom,
            Instant effectiveUntil,
            List<String> orderedEventCodes,
            Set<String> trustedExternalSystems) {
        public ApprovedPolicy {
            Objects.requireNonNull(contractType, "contractType");
            Objects.requireNonNull(effectiveFrom, "effectiveFrom");
            orderedEventCodes = List.copyOf(Objects.requireNonNull(orderedEventCodes, "orderedEventCodes"));
            trustedExternalSystems = Set.copyOf(Objects.requireNonNull(trustedExternalSystems, "trustedExternalSystems"));
        }
    }

    /** Only a trusted server-to-server integration may produce this record. */
    public record VerifiedExternalEvent(
            long depositId,
            String depositReference,
            ContractType contractType,
            String eventCode,
            String externalEventReference,
            String externalSystem,
            String verificationReference,
            Instant occurredAt) { }

    /** This assessment can NEVER be used as an authorization to post funds. */
    public record Assessment(
            long depositId,
            AssessmentStatus status,
            List<String> blockers,
            List<VerifiedExternalEvent> verifiedEvents) {
        public Assessment {
            Objects.requireNonNull(status, "status");
            blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
            verifiedEvents = List.copyOf(Objects.requireNonNull(verifiedEvents, "verifiedEvents"));
            if (status == AssessmentStatus.VERIFIED_FOR_INDEPENDENT_REVIEW && !blockers.isEmpty()) {
                throw new IllegalArgumentException("Verified assessment cannot have blockers");
            }
            if (status == AssessmentStatus.BLOCKED && blockers.isEmpty()) {
                throw new IllegalArgumentException("Blocked assessment requires a reason");
            }
        }

        public boolean postingAuthorized() { return false; }
        public boolean depositActivationAuthorized() { return false; }
    }
}
