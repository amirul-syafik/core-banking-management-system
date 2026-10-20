
package com.cbms.config;

import com.cbms.entity.AppUser;
import com.cbms.repository.AppUserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class JwtConfig {

    private static final Logger log =
            LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    public SecretKey jwtSecretKey(
            @Value("${app.jwt.secret}") String encodedSecret) {

        byte[] keyBytes = Base64.getDecoder()
                .decode(encodedSecret);

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must be at least 256 bits"
            );
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(
                new ImmutableSecret<>(jwtSecretKey)
        );
    }

    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            AppUserRepository users) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> defaults =
                JwtValidators.createDefault();

        OAuth2TokenValidator<Jwt> accountValidator = token -> {

            // Read issuer directly as String.
            // getIssuer() expects a URL-compatible value.
            String issuer = token.getClaimAsString("iss");

            if (!"cbms".equals(issuer)) {
                return invalid("Invalid token issuer");
            }

            String username = token.getSubject();

            if (username == null || username.isBlank()) {
                return invalid("Missing token subject");
            }

            try {
                AppUser user = users.findByUsername(username)
                        .orElse(null);

                if (user == null ||
                        !Boolean.TRUE.equals(user.getEnabled())) {
                    return invalid("User account is not active");
                }

                // Reject JWTs after assigned roles change.
                Set<String> currentRoles = user.getRoles()
                        .stream()
                        .map(role -> "ROLE_" + role.getRoleNm())
                        .collect(Collectors.toSet());

                Object claim = token.getClaims().get("roles");

                if (!(claim instanceof java.util.Collection<?> claimed)) {
                    return invalid("Missing token roles");
                }

                Set<String> tokenRoles = new HashSet<>();

                for (Object role : claimed) {
                    if (!(role instanceof String text)) {
                        return invalid("Invalid token roles");
                    }

                    // Only compare CBMS application roles.
                    // Spring Security may add authentication-factor authorities.
                    if (text.startsWith("ROLE_")) {
                        tokenRoles.add(text);
                    }
                }

                if (!currentRoles.equals(tokenRoles)) {

                    log.warn("JWT role mismatch for user: {}", username);
                    log.warn("Database roles: {}", currentRoles);
                    log.warn("JWT roles: {}", tokenRoles);

                    return invalid("User roles have changed");
                }

                return OAuth2TokenValidatorResult.success();

            } catch (Exception ex) {
                log.error(
                        "JWT account verification failed: {}",
                        ex.getClass().getSimpleName(),
                        ex
                );

                return invalid("Unable to verify token account");
            }
        };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        defaults,
                        accountValidator
                )
        );

        return decoder;
    }

    private static OAuth2TokenValidatorResult invalid(String message) {
        log.warn("JWT validation rejected: {}", message);

        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", message, null)
        );
    }
}
