package com.cbms.service;

import com.cbms.dto.BankAccountApplicationValidationResponse;
import com.cbms.entity.BankAccountApplication;
import com.cbms.entity.BankAccountApplicationHistory;
import com.cbms.repository.BankAccountApplicationHistoryRepository;
import com.cbms.repository.BankAccountApplicationRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

@Service
public class BankAccountApplicationCompletionService {

    private final BankAccountApplicationRepository applicationRepository;
    private final BankAccountApplicationHistoryRepository historyRepository;
    private final BankAccountApplicationValidationService validationService;
    private final ApprovedApplicationAccountCreator accountCreator;

    public BankAccountApplicationCompletionService(
            BankAccountApplicationRepository applicationRepository,
            BankAccountApplicationHistoryRepository historyRepository,
            BankAccountApplicationValidationService validationService,
            ApprovedApplicationAccountCreator accountCreator
    ) {
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
        this.validationService = validationService;
        this.accountCreator = accountCreator;
    }

    @Transactional
    public BankAccountApplication createAccount(
            Long applId,
            Authentication authentication
    ) {
        BankAccountApplication application =
                applicationRepository.findByIdForUpdate(applId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Application not found"
                        ));

        // Idempotent retry: return the completed application.
        if ("COMPLETED".equals(application.getApplSts())
                && application.getAcctId() != null) {
            return application;
        }

        if (!"APPROVED".equals(application.getApplSts())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only APPROVED applications can create accounts"
            );
        }

        if (application.getAcctId() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Application already has a linked account"
            );
        }

        // Recheck eligibility before account creation.
        BankAccountApplicationValidationResponse validation =
                validationService.validate(applId);

        if (!validation.valid()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Application validation failed: "
                            + String.join("; ", validation.errors())
            );
        }

        String username = authentication.getName();

        // Must use the existing account-creation logic.
        // The adapter must participate in this transaction.
        Long accountId = accountCreator.createAccount(
                application,
                username
        );

        if (accountId == null) {
            throw new IllegalStateException(
                    "Account creation did not return an account ID"
            );
        }

        application.setAcctId(accountId);
        application.setApplSts("COMPLETED");
        application.setUpdBy(username);
        application.setUpdDt(OffsetDateTime.now());

        applicationRepository.saveAndFlush(application);

        BankAccountApplicationHistory history =
                new BankAccountApplicationHistory();

        history.setApplId(applId);
        history.setOldSts("APPROVED");
        history.setNewSts("COMPLETED");
        history.setActionTyp("CREATE_ACCOUNT");
        history.setRemarks("Account created: " + accountId);
        history.setActionBy(username);

        historyRepository.saveAndFlush(history);

        return application;
    }
}
