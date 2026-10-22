package com.cbms.config;

import com.cbms.service.DatabaseUserDetailsService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.config.Customizer;
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

                        // Phase 21.5: documentation access (development only).
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

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/customers/*/documents"
                        )
                        .hasAnyRole("MAKER", "ADMIN")

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
                                "/api/reconciliation/exceptions/export")
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

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/transactions/*/reversals"
                        )
                        .hasRole("ADMIN")

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

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/financing/facilities"
                        ).hasAnyRole("MAKER", "ADMIN")

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
                .httpBasic(Customizer.withDefaults())
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
