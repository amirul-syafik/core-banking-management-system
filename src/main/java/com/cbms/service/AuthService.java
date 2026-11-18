package com.cbms.service;

import com.cbms.dto.LoginRequest;
import com.cbms.dto.LoginResponse;
import com.cbms.repository.AppUserRepository;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final AppUserRepository users;
    private final long expirationMinutes;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtEncoder jwtEncoder,
            AppUserRepository users,
            @Value("${app.jwt.expiration-minutes:30}")
            long expirationMinutes) {

        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.users = users;
        this.expirationMinutes = expirationMinutes;
    }

    public LoginResponse login(LoginRequest request) {

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.getUsername(),
                                request.getPassword()
                        )
                );

        Instant now = Instant.now();
        Instant expiresAt = now.plus(
                expirationMinutes,
                ChronoUnit.MINUTES
        );

        var roles = authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .toList();

        // Every issued JWT is bound to the current credential generation.
        // It becomes unusable immediately after a password change/reset.
        long credentialVersion = users.findByUsername(authentication.getName())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Invalid account"))
                .getCredentialVersion();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("cbms")
                .subject(authentication.getName())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("roles", roles)
                .claim("cv", credentialVersion)
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();

        return new LoginResponse(
                token,
                "Bearer",
                expirationMinutes * 60
        );
    }
}
