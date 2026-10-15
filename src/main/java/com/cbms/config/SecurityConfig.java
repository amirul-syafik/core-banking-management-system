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
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers("/api/customers/*/documents/**")
                        .denyAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/customers/*/identifications",
                                "/api/customers/*/identifications/*"
                        )
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/customers/*/identifications"
                        )
                        .hasAnyRole("MAKER", "ADMIN")

                        .requestMatchers("/api/customers/*/identifications/**")
                        .denyAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/customers",
                                "/api/customers/**"
                        )
                        .hasAnyRole("VIEWER", "MAKER", "ADMIN")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/customers/*/status"
                        )
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/customers",
                                "/api/customers/**"
                        )
                        .hasAnyRole("MAKER", "ADMIN")

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