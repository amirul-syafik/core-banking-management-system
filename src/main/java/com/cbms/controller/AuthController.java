package com.cbms.controller;

import com.cbms.dto.LoginRequest;
import com.cbms.dto.LoginResponse;
import com.cbms.dto.ChangePasswordRequest;
import com.cbms.dto.ForgotPasswordRequest;
import com.cbms.service.PasswordChangeService;
import com.cbms.service.PasswordRecoveryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import com.cbms.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordChangeService changes;
    private final PasswordRecoveryService recovery;

    public AuthController(AuthService authService, PasswordChangeService changes, PasswordRecoveryService recovery) {
        this.authService = authService;
        this.changes = changes;
        this.recovery = recovery;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request) {

        return authService.login(request);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication auth) {
        changes.change(auth.getName(), request);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        recovery.requestAssistance(request.username());
    }
}
