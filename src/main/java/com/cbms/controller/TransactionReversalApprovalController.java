package com.cbms.controller;

import com.cbms.dto.TransactionReversalRequest;
import com.cbms.entity.TransactionReversalApproval;
import com.cbms.service.TransactionReversalApprovalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transaction-reversal-requests")
public class TransactionReversalApprovalController {
    private final TransactionReversalApprovalService service;

    public TransactionReversalApprovalController(TransactionReversalApprovalService service) {
        this.service = service;
    }

    @GetMapping
    public Page<TransactionReversalApproval> list(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return service.list(page, size);
    }

    @GetMapping("/eligibility")
    public java.util.Map<Long, String> eligibility(@RequestParam java.util.List<Long> ids) {
        return service.eligibility(ids);
    }

    @PostMapping("/{txnId}")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionReversalApproval submit(@PathVariable Long txnId,
                                              @Valid @RequestBody TransactionReversalRequest request, Authentication user) {
        return service.submit(txnId, request, user);
    }

    public record Decision(String reason) {
    }

    @PostMapping("/{id}/approve")
    public TransactionReversalApproval approve(@PathVariable Long id, @RequestBody(required = false) Decision body,
                                               Authentication user) {
        return service.decide(id, true, body == null ? null : body.reason(), user);
    }

    @PostMapping("/{id}/reject")
    public TransactionReversalApproval reject(@PathVariable Long id, @RequestBody Decision body,
                                              Authentication user) {
        return service.decide(id, false, body.reason(), user);
    }
}
