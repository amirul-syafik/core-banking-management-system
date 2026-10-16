package com.cbms.controller;

import com.cbms.dto.BankAccountStatusUpdateRequest;
import com.cbms.dto.BankAccountResponse;
import com.cbms.entity.BankAccount;
import com.cbms.entity.BankAccountAuditHistory;
import com.cbms.repository.BankAccountAuditHistoryRepository;
import com.cbms.repository.BankAccountRepository;
import com.cbms.service.BankAccountStatusService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class BankAccountStatusController {

    private final BankAccountStatusService statusService;
    private final BankAccountAuditHistoryRepository auditRepository;
    private final BankAccountRepository accountRepository;

    public BankAccountStatusController(
            BankAccountStatusService statusService,
            BankAccountAuditHistoryRepository auditRepository,
            BankAccountRepository accountRepository
    ) {
        this.statusService = statusService;
        this.auditRepository = auditRepository;
        this.accountRepository = accountRepository;
    }

    @PatchMapping("/{acctId}/status")
    public BankAccountResponse updateStatus(
            @PathVariable Long acctId,
            @Valid @RequestBody BankAccountStatusUpdateRequest request,
            Authentication authentication
    ) {
        BankAccount account = statusService.updateStatus(
                acctId, request, authentication
        );

        return new BankAccountResponse(
                account.getAcctId(),
                account.getCustId(),
                account.getAcctNo(),
                account.getAcctTyp(),
                account.getCcyCd(),
                account.getAcctSts(),
                account.getOpenDt(),
                account.getCloseDt(),
                account.getCrtBy(),
                account.getCrtDt()
        );
    }

    @GetMapping("/{acctId}/audit-history")
    public Page<BankAccountAuditHistory> getAuditHistory(
            @PathVariable Long acctId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        if (!accountRepository.existsById(acctId)) {
            throw new IllegalArgumentException(
                    "Bank account not found: " + acctId
            );
        }

        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Invalid pagination parameters"
            );
        }

        return auditRepository.findByAcctId(
                acctId,
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                Sort.Direction.DESC,
                                "changedDt",
                                "auditId"
                        )
                )
        );
    }
}
