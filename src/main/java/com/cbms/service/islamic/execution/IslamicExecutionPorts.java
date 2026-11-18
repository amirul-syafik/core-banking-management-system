package com.cbms.service.islamic.execution;

import java.time.Instant;
import java.util.Optional;

import com.cbms.service.islamic.execution.IslamicExecutionEvidence.ApprovedPolicy;
import com.cbms.service.islamic.execution.IslamicExecutionEvidence.ContractType;
import com.cbms.service.islamic.execution.IslamicExecutionEvidence.DepositSnapshot;
import com.cbms.service.islamic.execution.IslamicExecutionEvidence.VerifiedExternalEvent;

/**
 * Integration seams intentionally have NO default, permissive implementations.
 * A production caller must bind all four ports to independently reviewed sources.
 */
public final class IslamicExecutionPorts {
    private IslamicExecutionPorts() { }

    @FunctionalInterface
    public interface DepositStateReader {
        Optional<DepositSnapshot> findDeposit(long depositId);
    }

    @FunctionalInterface
    public interface BankApprovedPolicyReader {
        Optional<ApprovedPolicy> currentPolicy(ContractType contractType, Instant asOf);
    }

    @FunctionalInterface
    public interface TrustedExternalVerificationGateway {
        Optional<VerifiedExternalEvent> verify(DepositSnapshot deposit, ApprovedPolicy policy, String eventCode);
    }

    @FunctionalInterface
    public interface ExternalReferenceRegistry {
        /** True if an authenticated external event is already bound to another deposit. */
        boolean isLinkedToDifferentDeposit(String externalSystem, String externalEventReference, long depositId);
    }
}
