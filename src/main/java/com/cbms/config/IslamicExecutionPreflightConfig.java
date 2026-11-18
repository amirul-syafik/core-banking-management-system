package com.cbms.config;

import java.time.Clock;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cbms.service.islamic.execution.IslamicExecutionPreflightService;
import com.cbms.service.islamic.execution.IslamicExecutionPorts.BankApprovedPolicyReader;
import com.cbms.service.islamic.execution.IslamicExecutionPorts.DepositStateReader;
import com.cbms.service.islamic.execution.IslamicExecutionPorts.ExternalReferenceRegistry;
import com.cbms.service.islamic.execution.IslamicExecutionPorts.TrustedExternalVerificationGateway;

/**
 * Read-only wiring for Islamic execution preflight. Unconfigured dependencies fail closed.
 * A future adapter must be backed by bank-approved policy and independently
 * authenticated counterparty events, never maker-entered documentary values.
 */
@Configuration
public class IslamicExecutionPreflightConfig {

    // The database-backed BankIslamicApprovedPolicyReader bean is authoritative.
    // Missing/unattested bank policies cause a BLOCKED result; never invent a policy.

    @Bean
    @ConditionalOnMissingBean(TrustedExternalVerificationGateway.class)
    public TrustedExternalVerificationGateway unconfiguredVerificationGateway() {
        // No authenticated trading/investment integration has been supplied.
        return (deposit, policy, eventCode) -> Optional.empty();
    }

    // The database-backed BankIslamicExternalReferenceRegistry is authoritative.
    // No app/browser route can insert or certify external receipts.

    @Bean
    public IslamicExecutionPreflightService islamicExecutionPreflight(
            DepositStateReader deposits,
            BankApprovedPolicyReader policies,
            TrustedExternalVerificationGateway gateway,
            ExternalReferenceRegistry references) {
        return new IslamicExecutionPreflightService(deposits, policies, gateway,
                references, Clock.systemUTC());
    }
}
