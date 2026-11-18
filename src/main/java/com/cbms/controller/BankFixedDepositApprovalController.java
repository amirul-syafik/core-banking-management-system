package com.cbms.controller;

import com.cbms.service.BankFixedDepositApprovalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fd-approval-requests")
public class BankFixedDepositApprovalController {
    private final BankFixedDepositApprovalService service;
    public BankFixedDepositApprovalController(BankFixedDepositApprovalService service) { this.service = service; }

    public record SubmitRequest(@NotNull Long fdId, @NotBlank String actionType,
                                @NotNull Map<String,Object> payload, @NotBlank String reason) {}
    public record DecisionRequest(String reason) {}
    public record AssignRequest(@NotBlank String checkerUsername) {}

    @GetMapping
    public List<Map<String,Object>> list(@RequestParam(defaultValue="50") int limit) {
        return service.list(limit);
    }

    @GetMapping("/{requestId}")
    public Map<String,Object> get(@PathVariable Long requestId) {
        return service.get(requestId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> submit(@Valid @RequestBody SubmitRequest request, Authentication actor) {
        return service.submit(request.fdId(), request.actionType(), request.payload(), request.reason(), actor);
    }

    @PostMapping("/{requestId}/assign")
    public Map<String,Object> assign(@PathVariable Long requestId,
                                      @Valid @RequestBody AssignRequest body,
                                      Authentication actor) {
        return service.assign(requestId, body.checkerUsername(), actor);
    }

    @PostMapping("/{requestId}/approve")
    public Map<String,Object> approve(@PathVariable Long requestId, @RequestBody(required=false) DecisionRequest body, Authentication actor) {
        return service.decide(requestId, true, body == null ? null : body.reason(), actor);
    }

    @PostMapping("/{requestId}/reject")
    public Map<String,Object> reject(@PathVariable Long requestId, @RequestBody DecisionRequest body, Authentication actor) {
        return service.decide(requestId, false, body == null ? null : body.reason(), actor);
    }
}
