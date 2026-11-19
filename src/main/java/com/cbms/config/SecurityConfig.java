package com.cbms.config;

import com.cbms.service.DatabaseUserDetailsService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(auth -> auth

                        // Documentation access (development only).
                        // Keep this before the final denyAll rule.
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        )
                        .hasRole("ADMIN")

                        .requestMatchers("/api/health")
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login"
                        )
                        .permitAll()

                        // Public request: generic response, no account enumeration.
                        .requestMatchers(HttpMethod.POST, "/api/auth/forgot-password")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/change-password")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")
                        .requestMatchers("/api/auth/**")
                        .denyAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/customers/*/documents"
                        )
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/customers/*/profile-picture"
                        )
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/customers/*/profile-picture"
                        )
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/customers/*/profile-picture"
                        )
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/customers/*/documents",
                                "/api/customers/*/documents/*/download"
                        )
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers("/api/customers/*/documents/**")
                        .denyAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/customers/*/identifications",
                                "/api/customers/*/identifications/*"
                        )
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/customers/*/identifications"
                        )
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(HttpMethod.PUT, "/api/customers/*/identifications/*")
                       .denyAll()

                       .requestMatchers("/api/customers/*/identifications/**")
                        .denyAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/accounts/*",
                                "/api/customers/*/accounts"
                        )
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/customers/*/accounts"
                        )
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/accounts/*/status").denyAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/accounts/*/status-requests")
                                .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/accounts/*/status-requests")
                                .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/accounts/*/status-requests/*/decision")
                                .hasRole("ADMIN")

                        .requestMatchers(
                                "/api/accounts/*/status-requests/**")
                                .denyAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/accounts/*/audit-history"
                        )
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/accounts/*/balance"
                        )
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/accounts/*/deposits",
                                "/api/accounts/*/withdrawals"
                        )
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/transaction-audit")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/transaction-reversal-requests",
                                "/api/transaction-reversal-requests/*",
                                "/api/transaction-reversal-requests/eligibility")
                                .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/transaction-reversal-requests/*")
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/transaction-reversal-requests/*/approve",
                                "/api/transaction-reversal-requests/*/reject")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/accounts/*/transactions",
                                "/api/accounts/*/transactions/inquiry",
                                "/api/transactions/*"
                        )
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(HttpMethod.GET,
                                "/api/reconciliation/report",
                                "/api/reconciliation/export",
                                "/api/reconciliation/investigation/*",
                                "/api/reconciliation/exceptions",
                                "/api/reconciliation/exceptions/export",
                                "/api/reconciliation/journal-integrity/*")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/reconciliation/accounts",
                                "/api/reconciliation/accounts/*"
                        )
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/accounts/*/transfers"
                        )
                        .hasAnyRole("MAKER", "ADMIN")

                        // All financial reversals must follow maker-checker approval.
                        // Prevent direct reversal even for ADMIN; the approval service
                        // invokes BankTransactionReversalService internally after approval.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/transactions/*/reversals"
                        )
                        .denyAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/products",
                                "/api/products/**"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/products"
                        ).hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/products/*"
                        ).hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/products/*/status"
                        ).hasRole("ADMIN")

                        // Islamic deposit DRAFT readiness and immutable snapshot are read-only.
                        // Profit terms review is separately role-scoped below; execution remains blocked.
                        .requestMatchers(HttpMethod.GET,
                                "/api/islamic/deposits/schemes/*/readiness",
                                "/api/islamic/deposits/*/contract")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")
                        // Islamic deposit proposal review only; no posting endpoints.
                        .requestMatchers(HttpMethod.GET, "/api/islamic/deposits/*/profit-terms")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/islamic/deposits/*/profit-terms")
                        .hasAnyRole("MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/islamic/deposits/profit-terms/*/submit")
                        .hasAnyRole("MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/islamic/deposits/profit-terms/*/decision")
                        .hasRole("ADMIN")
                        // Independent maker/checker GL account maintenance (configuration only).
                        .requestMatchers(HttpMethod.GET, "/api/gl-maintenance/accounts", "/api/gl-maintenance/requests")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/gl-maintenance/requests")
                        .hasAnyRole("MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/gl-maintenance/requests/*/decision")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/gl-maintenance/**")
                        .denyAll()

                        // Authenticated read-only ISO country and currency reference options.
                        .requestMatchers(HttpMethod.GET,
                                "/api/reference-data/countries",
                                "/api/reference-data/currencies")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")
                        .requestMatchers("/api/reference-data/**")
                        .denyAll()

                        // Shared active GL catalog is read-only; no ledger maintenance/posting rights.
                        .requestMatchers(HttpMethod.GET, "/api/ledger-accounts/eligible")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")
                        .requestMatchers("/api/ledger-accounts/**")
                        .denyAll()

                        // Proposed GL mappings: review only, not journal posting.
                        .requestMatchers(HttpMethod.GET, "/api/islamic/deposits/*/ledger-mapping")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/islamic/deposits/*/ledger-mapping")
                        .hasAnyRole("MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/islamic/deposits/ledger-mapping/*/submit")
                        .hasAnyRole("MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/islamic/deposits/ledger-mapping/*/decision")
                        .hasRole("ADMIN")

                        // Documentary policy governance, never a financial execution grant.
                        .requestMatchers(HttpMethod.GET, "/api/islamic/execution-policies")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/islamic/execution-policies")
                        .hasAnyRole("MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/islamic/execution-policies/*/submit")
                        .hasAnyRole("MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/islamic/execution-policies/*/decision")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/islamic/execution-policies/**")
                        .denyAll()

                        // Chronology review only (no contract execution or posting).
                        .requestMatchers(HttpMethod.GET, "/api/islamic/deposits/*/execution-timeline")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/islamic/deposits/*/execution-timeline")
                        .hasAnyRole("MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/islamic/deposits/execution-timeline/*/submit")
                        .hasAnyRole("MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/islamic/deposits/execution-timeline/*/decision")
                        .hasRole("ADMIN")

                        // Posting readiness is read-only. No posting route exists.
                        .requestMatchers(HttpMethod.GET, "/api/islamic/deposits/*/posting-readiness")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")
                        // External event verification status only; fail closed.
                        .requestMatchers(HttpMethod.GET, "/api/islamic/deposits/*/external-verification")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/islamic/deposits/*/phase10-gates")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")
                        // Review documentary execution evidence and
                        // accounting-configuration references only, NEVER GL posting.
                        .requestMatchers(HttpMethod.GET, "/api/islamic/deposits/*/execution-evidence")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/islamic/deposits/*/execution-evidence")
                        .hasAnyRole("MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/islamic/deposits/execution-evidence/*/submit")
                        .hasAnyRole("MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/islamic/deposits/execution-evidence/*/decision")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/islamic/deposits/**")
                        .denyAll()

                        // Islamic contract configuration is separate from financial booking.
                        .requestMatchers(HttpMethod.GET, "/api/islamic/contracts")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/islamic/contracts")
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/islamic/contracts/*/submit")
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/islamic/contracts/*/decision")
                        .hasRole("ADMIN")

                        .requestMatchers("/api/islamic/contracts/**")
                        .denyAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/schemes",
                                "/api/schemes/**"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/schemes"
                        ).hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/schemes/*"
                        ).hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/schemes/*/status"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/rates/**"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/rates/base",
                                "/api/rates/scheme"
                        ).hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/account-applications",
                                "/api/account-applications/**"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/account-applications/*/create-account"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/account-applications"
                        ).hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/account-applications/*/approve",
                                "/api/account-applications/*/reject"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/account-applications/*/submit"
                        ).hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/financing/facilities",
                                "/api/financing/facilities/**"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        // There is no direct facility-creation API. Booking must go
                        // through the approved financing application workflow.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/financing/applications",
                                "/api/financing/applications/**"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/financing/applications"
                        ).hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/financing/applications/*/submit"
                        ).hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/financing/applications/*/approve",
                                "/api/financing/applications/*/reject"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/financing/facilities"
                        ).denyAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/financing/applications/*/book-facility"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/financing/facilities/*/schedule/generate"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/financing/facilities/*/schedule"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/financing/facilities/*/reprice"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/financing/facilities/*/disbursement"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/financing/facilities/*/disbursement"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/financing/facilities/*/repayments"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/financing/facilities/*/repayments"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/financing/facilities/*/outstanding",
                                "/api/financing/facilities/*/summary"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/financing/facilities/*/close"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/financing/facilities/*/closure"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/credit-lines/facilities"
                        ).hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/credit-lines/facilities/**"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/credit-lines/facilities/*/activate",
                                "/api/credit-lines/facilities/*/limit-adjustments"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/credit-lines/facilities/*/limit",
                                "/api/credit-lines/facilities/*/limit-adjustments"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/credit-lines/facilities/*/drawdowns"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/credit-lines/facilities/*/drawdowns"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/credit-lines/facilities/*/repayments"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/credit-lines/facilities/*/repayments"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/credit-lines/facilities/*/stocks",
                                "/api/credit-lines/stocks/*/settle"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/credit-lines/facilities/*/stocks",
                                "/api/credit-lines/stocks/*"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/credit-lines/facilities/*/interest/accrue"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/credit-lines/facilities/*/interest/**"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/credit-lines/facilities/*/suspend",
                                "/api/credit-lines/facilities/*/reactivate",
                                "/api/credit-lines/facilities/*/close"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/credit-lines/monitoring",
                                "/api/credit-lines/facilities/*/monitoring",
                                "/api/credit-lines/facilities/*/status-history"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        // Each notification API request is scoped to its authenticated user.
                        .requestMatchers(HttpMethod.GET,
                                "/api/notifications", "/api/notifications/unread-count")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api/notifications/read-all", "/api/notifications/*/read")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")
                        .requestMatchers("/api/notifications/**").denyAll()

                        .requestMatchers(HttpMethod.GET, "/api/fd-approval-requests", "/api/fd-approval-requests/*")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/fd-approval-requests")
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/fd-approval-requests/*/assign")
                        .hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST,
                                "/api/fd-approval-requests/*/approve",
                                "/api/fd-approval-requests/*/reject")
                        .hasRole("ADMIN")

                        // Financial actions may only be executed from the approval service.
                        .requestMatchers(HttpMethod.POST,
                                "/api/fixed-deposits/*/maturity/settle",
                                "/api/fixed-deposits/*/renew",
                                "/api/fixed-deposits/*/premature-withdraw")
                        .denyAll()
.requestMatchers(
                                HttpMethod.POST,
                                "/api/fixed-deposits"
                        ).hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/fixed-deposits",
                                "/api/fixed-deposits/*"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/fixed-deposits/*/place"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/fixed-deposits/*/placement"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/fixed-deposits/maturity/calculate"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/fixed-deposits/*/maturity/preview",
                                "/api/fixed-deposits/*/maturity/validate"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/fixed-deposits/*/maturity/settle"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/fixed-deposits/*/maturity/process"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/fixed-deposits/*/renew"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/fixed-deposits/*/premature-withdraw"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/fixed-deposits/interest/calculate"
                        ).hasAnyRole("ADMIN", "MAKER", "VIEWER")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/fixed-deposits/*/interest/accrue"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/fixed-deposits/*/interest/accruals",
                                "/api/fixed-deposits/*/interest/accrued-total"
                        ).hasAnyRole("ADMIN", "MAKER", "VIEWER")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/fixed-deposits/*/interest/payout"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/fixed-deposits/*/interest/payouts",
                                "/api/fixed-deposits/*/interest/unpaid-total"
                        ).hasAnyRole("ADMIN", "MAKER", "VIEWER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/fixed-deposits/monitoring/**"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")


                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/vehicles", "/api/vehicles/**")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/vehicles")
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/vehicles/**")
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/vehicles/**")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/collaterals", "/api/collaterals/**")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/collaterals")
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/collaterals/*/vehicle")
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/collaterals/**")
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/collaterals/**")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/collaterals/*/valuations",
                                "/api/collaterals/*/valuations/**")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/collaterals/*/valuations")
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/financing-collaterals/**")
                        .hasAnyRole("VIEWER","MAKER","ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/financing-collaterals/*/activate")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/financing-collaterals")
                        .hasAnyRole("MAKER","ADMIN")

                        .requestMatchers(HttpMethod.PUT, "/api/customers/**")
                       .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/customers",
                                "/api/customers/**"
                        )
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/collateral-monitoring/**"
                        ).hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/financing-collaterals/*/release"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/reports/*",
                                "/api/reports/*/export")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                "/api/reports/**")
                        .denyAll()

                        .requestMatchers(HttpMethod.GET, "/api/customer-status-history")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers("/api/customer-status-history/**")
                        .denyAll()

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/customers/*/status")
                        .denyAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/customers/*/status-requests")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/customers/*/status-requests")
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/customers/*/status-requests/*/decision")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                "/api/customers/*/status-requests/**")
                        .denyAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/customers",
                                "/api/customers/**"
                        )
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/workflows/definitions",
                                "/api/workflows/definitions/*")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/workflows/definitions",
                                "/api/workflows/definitions/*/steps",
                                "/api/workflows/definitions/*/activate")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/workflows/definitions/**")
                        .denyAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/workflows/monitoring/summary",
                                "/api/workflows/monitoring/pending",
                                "/api/workflows/monitoring/overdue",
                                "/api/workflows/monitoring/statistics")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")
                        .requestMatchers("/api/workflows/monitoring/**")
                        .denyAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/workflows/history")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")
                        .requestMatchers("/api/workflows/history/**")
                        .denyAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/workflows/instances/mine",
                                "/api/workflows/instances/*",
                                "/api/workflows/instances/*/history")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/workflows/instances")
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                "/api/workflows/instances/**")
                        .denyAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/workflows/tasks/queue",
                                "/api/workflows/tasks/mine",
                                "/api/workflows/tasks/instance/*")
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(HttpMethod.POST,
                                "/api/workflows/tasks/initialize/*")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/workflows/tasks/*/claim")
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/workflows/tasks/*/assign")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/workflows/tasks/*/approve",
                                "/api/workflows/tasks/*/reject")
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                "/api/workflows/tasks/**")
                        .denyAll()

                        .requestMatchers(
                                "/api/admin/users",
                                "/api/admin/users/**")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                "/api/admin/parameters",
                                "/api/admin/parameters/**")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                "/api/admin/activity-audit",
                                "/api/admin/activity-audit/**")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                "/api/admin/logs",
                                "/api/admin/logs/**")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                "/api/admin/monitoring",
                                "/api/admin/monitoring/**")
                        .hasRole("ADMIN")

                        .anyRequest()
                        .denyAll()
                )
                // Bearer-only API: HTTP Basic causes the browser's native credential popup on 401.
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, ignored) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"Session expired or authentication required\"}");
                }))
                .oauth2ResourceServer(oauth ->
                        oauth.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter()
                                )
                        )
                );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );

        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            DatabaseUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(provider);
    }
}
