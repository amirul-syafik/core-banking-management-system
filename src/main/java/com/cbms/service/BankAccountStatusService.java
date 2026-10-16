package com.cbms.service;

import com.cbms.dto.BankAccountStatusUpdateRequest;
import com.cbms.entity.BankAccount;
import com.cbms.entity.BankAccountAuditHistory;
import com.cbms.repository.BankAccountRepository;
import com.cbms.repository.BankAccountAuditHistoryRepository;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

@Service
public class BankAccountStatusService {

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS =
            Map.of(
                    "PENDING", Set.of("ACTIVE", "CLOSED"),
                    "ACTIVE", Set.of("DORMANT", "FROZEN", "CLOSED"),
                    "DORMANT", Set.of("ACTIVE", "FROZEN", "CLOSED"),
                    "FROZEN", Set.of("ACTIVE", "CLOSED"),
                    "CLOSED", Set.of()
            );

    private final BankAccountRepository accountRepository;
    private final BankAccountAuditHistoryRepository auditRepository;

    public BankAccountStatusService(
            BankAccountRepository accountRepository,
            BankAccountAuditHistoryRepository auditRepository
    ) {
        this.accountRepository = accountRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public BankAccount updateStatus(
            Long acctId,
            BankAccountStatusUpdateRequest request,
            Authentication authentication
    ) {
        BankAccount account = accountRepository
                .findByIdForUpdate(acctId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Bank account not found: " + acctId
                        )
                );

        String currentStatus = account.getAcctSts();
        String newStatus = request.status();

        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(
                currentStatus,
                Set.of()
        );

        if (!allowed.contains(newStatus)) {
            throw new IllegalArgumentException(
                    "Invalid account status transition: "
                            + currentStatus + " -> " + newStatus
            );
        }

        // Temporary safeguard until the ledger/balance module exists.
        // Closing is intentionally blocked to avoid bypassing balance checks.
        if ("CLOSED".equals(newStatus)) {
            throw new IllegalArgumentException(
                    "Account closure is unavailable until balance "
                            + "and pending-transaction checks are implemented"
            );
        }

        OffsetDateTime now = OffsetDateTime.now();

        account.setAcctSts(newStatus);
        account.setUpdDt(now);
        account.setUpdBy(authentication.getName());

        BankAccountAuditHistory audit =
                new BankAccountAuditHistory();

        audit.setAcctId(acctId);
        audit.setActionTyp("STATUS_CHANGE");
        audit.setOldVal(currentStatus);
        audit.setNewVal(newStatus);
        audit.setChangedBy(authentication.getName());
        audit.setChangedDt(now);

        accountRepository.save(account);
        auditRepository.save(audit);

        return account;
    }
}
