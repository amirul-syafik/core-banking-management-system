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
import java.util.List;

@Service
public class BankAccountApplicationWorkflowService {

    private final BankAccountApplicationRepository applicationRepository;
    private final BankAccountApplicationHistoryRepository historyRepository;
    private final BankAccountApplicationValidationService validationService;

    public BankAccountApplicationWorkflowService(
            BankAccountApplicationRepository applicationRepository,
            BankAccountApplicationHistoryRepository historyRepository,
            BankAccountApplicationValidationService validationService
    ) {
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
        this.validationService = validationService;
    }

    @Transactional
    public BankAccountApplication submit(
            Long applId,
            Authentication authentication
    ) {
        BankAccountApplication application = getLocked(applId);

        requireStatus(application, "DRAFT");

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

        application.setApplSts("SUBMITTED");
        application.setSubmittedBy(username);
        application.setSubmittedDt(OffsetDateTime.now());
        application.setUpdBy(username);

        applicationRepository.saveAndFlush(application);

        recordHistory(
                application.getApplId(),
                "DRAFT",
                "SUBMITTED",
                "SUBMIT",
                null,
                username
        );

        return application;
    }


    @Transactional
    public BankAccountApplication approve(
            Long applId,
            String remarks,
            Authentication authentication
    ) {
        BankAccountApplication application = getLocked(applId);

        requireStatus(application, "SUBMITTED");

        String username = authentication.getName();

        // Maker-checker separation
        enforceMakerChecker(application, username);

        // Revalidate all eligibility requirements,
        // including customer status and KYC.
        BankAccountApplicationValidationResponse validation =
                validationService.validate(applId);

        if (!validation.valid()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Application validation failed: "
                            + String.join("; ", validation.errors())
            );
        }

        application.setApplSts("APPROVED");
        application.setReviewedBy(username);
        application.setReviewedDt(OffsetDateTime.now());
        application.setReviewRemarks(
                remarks == null ? null : remarks.trim()
        );
        application.setUpdBy(username);

        applicationRepository.saveAndFlush(application);

        recordHistory(
                applId,
                "SUBMITTED",
                "APPROVED",
                "APPROVE",
                remarks,
                username
        );

        return application;
    }


    @Transactional
    public BankAccountApplication reject(
            Long applId,
            String remarks,
            Authentication authentication
    ) {
        BankAccountApplication application = getLocked(applId);

        requireStatus(application, "SUBMITTED");

        String username = authentication.getName();

        enforceMakerChecker(application, username);

        if (remarks == null || remarks.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rejection remarks are required"
            );
        }

        application.setApplSts("REJECTED");
        application.setReviewedBy(username);
        application.setReviewedDt(OffsetDateTime.now());
        application.setReviewRemarks(remarks.trim());
        application.setUpdBy(username);

        applicationRepository.saveAndFlush(application);

        recordHistory(
                applId,
                "SUBMITTED",
                "REJECTED",
                "REJECT",
                remarks.trim(),
                username
        );

        return application;
    }

    @Transactional(readOnly = true)
    public List<BankAccountApplicationHistory> history(Long applId) {
        if (!applicationRepository.existsById(applId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Application not found"
            );
        }

        return historyRepository
                .findByApplIdOrderByActionDtAscHistIdAsc(applId);
    }

    private BankAccountApplication getLocked(Long applId) {
        return applicationRepository.findByIdForUpdate(applId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Application not found"
                ));
    }

    private void requireStatus(
            BankAccountApplication application,
            String expected
    ) {
        if (!expected.equals(application.getApplSts())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Application must be " + expected
            );
        }
    }

    private void enforceMakerChecker(
            BankAccountApplication application,
            String username
    ) {
        if (username.equals(application.getCrtBy())
                || username.equals(application.getSubmittedBy())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Maker-checker violation: reviewer cannot "
                            + "review their own application"
            );
        }
    }

    private void recordHistory(
            Long applId,
            String oldStatus,
            String newStatus,
            String action,
            String remarks,
            String username
    ) {
        BankAccountApplicationHistory history =
                new BankAccountApplicationHistory();

        history.setApplId(applId);
        history.setOldSts(oldStatus);
        history.setNewSts(newStatus);
        history.setActionTyp(action);
        history.setRemarks(remarks);
        history.setActionBy(username);

        historyRepository.save(history);
    }
}
