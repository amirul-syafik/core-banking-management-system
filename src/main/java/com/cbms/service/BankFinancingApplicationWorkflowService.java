package com.cbms.service;

import com.cbms.dto.BankFinancingApprovalRequest;
import com.cbms.dto.BankFinancingRejectionRequest;
import com.cbms.dto.BankFinancingApplicationValidationResponse;
import com.cbms.entity.BankFinancingApplication;
import com.cbms.entity.BankFinancingApplicationHistory;
import com.cbms.entity.BankScheme;
import com.cbms.repository.BankFinancingApplicationRepository;
import com.cbms.repository.BankFinancingApplicationHistoryRepository;
import com.cbms.repository.BankSchemeRepository;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class BankFinancingApplicationWorkflowService {

    private final BankFinancingApplicationRepository applicationRepository;
    private final BankFinancingApplicationHistoryRepository historyRepository;
    private final BankFinancingApplicationValidationService validationService;
    private final BankSchemeRepository schemeRepository;
    private final JdbcTemplate jdbcTemplate;
    private BankApprovalNotificationService notifications;

    @Autowired
    public void setNotificationService(BankApprovalNotificationService notifications) {
        this.notifications = notifications;
    }


    public BankFinancingApplicationWorkflowService(
            BankFinancingApplicationRepository applicationRepository,
            BankFinancingApplicationHistoryRepository historyRepository,
            BankFinancingApplicationValidationService validationService,
            BankSchemeRepository schemeRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
        this.validationService = validationService;
        this.schemeRepository = schemeRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public BankFinancingApplication submit(
            Long applId,
            Authentication authentication
    ) {
        BankFinancingApplication application = lock(applId);

        requireStatus(application, "DRAFT");
        requireValid(application.getApplId());

        String username = authentication.getName();

        application.setApplSts("SUBMITTED");
        application.setSubmittedBy(username);
        application.setSubmittedDt(OffsetDateTime.now());
        application.setUpdBy(username);

        saveHistory(
                application.getApplId(),
                "DRAFT",
                "SUBMITTED",
                "SUBMIT",
                null,
                username
        );

        application = applicationRepository.saveAndFlush(application);
        if (notifications != null) notifications.notifyRole("ADMIN", username, "FINANCING",
                "FINANCING_APPLICATION", applId, "PENDING", "Financing application awaiting approval",
                "A financing application has been submitted for review");
        return application;
    }

    @Transactional
    public BankFinancingApplication approve(
            Long applId,
            BankFinancingApprovalRequest request,
            Authentication authentication
    ) {
        BankFinancingApplication application = lock(applId);

        requireStatus(application, "SUBMITTED");

        String username = authentication.getName();
        requireIndependentChecker(application, authentication);
        requireValid(application.getApplId());

        BankScheme scheme = schemeRepository
                .findById(application.getSchmId())
                .orElseThrow(() -> conflict("Financing scheme not found"));

        validateApprovedTerms(request, scheme);
        validateApprovedRate(application, request.apprRate());

        application.setApprAmt(request.apprAmt());
        application.setApprTenor(request.apprTenor());
        application.setApprRate(request.apprRate());

        application.setApplSts("APPROVED");
        application.setReviewedBy(username);
        application.setReviewedDt(OffsetDateTime.now());
        application.setReviewRemarks(request.remarks());
        application.setUpdBy(username);

        saveHistory(
                application.getApplId(),
                "SUBMITTED",
                "APPROVED",
                "APPROVE",
                request.remarks(),
                username
        );

        application = applicationRepository.saveAndFlush(application);
        if (notifications != null) notifications.notifyUser(application.getSubmittedBy(), "FINANCING",
                "FINANCING_APPLICATION", applId, "DECIDED", "Financing application approved",
                "Your financing application was approved");
        return application;
    }

    @Transactional
    public BankFinancingApplication reject(
            Long applId,
            BankFinancingRejectionRequest request,
            Authentication authentication
    ) {
        BankFinancingApplication application = lock(applId);

        requireStatus(application, "SUBMITTED");

        String username = authentication.getName();
        requireIndependentChecker(application, authentication);

        if (request.remarks() == null || request.remarks().isBlank()) {
            throw conflict("Rejection remarks are required");
        }

        application.setApplSts("REJECTED");
        application.setReviewedBy(username);
        application.setReviewedDt(OffsetDateTime.now());
        application.setReviewRemarks(request.remarks());
        application.setUpdBy(username);

        saveHistory(
                application.getApplId(),
                "SUBMITTED",
                "REJECTED",
                "REJECT",
                request.remarks(),
                username
        );

        application = applicationRepository.saveAndFlush(application);
        if (notifications != null) notifications.notifyUser(application.getSubmittedBy(), "FINANCING",
                "FINANCING_APPLICATION", applId, "DECIDED", "Financing application rejected",
                "Your financing application was rejected");
        return application;
    }

    @Transactional(readOnly = true)
    public List<BankFinancingApplicationHistory> history(Long applId) {

        if (!applicationRepository.existsById(applId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Financing application not found"
            );
        }

        return historyRepository
                .findByApplIdOrderByActionDtAscHistIdAsc(applId);
    }

    private BankFinancingApplication lock(Long applId) {
        return applicationRepository.findByIdForUpdate(applId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Financing application not found"
                ));
    }

    private void requireStatus(
            BankFinancingApplication application,
            String expected
    ) {
        if (!expected.equals(application.getApplSts())) {
            throw conflict(
                    "Application must be " + expected
                            + ", current status: "
                            + application.getApplSts()
            );
        }
    }

    private void requireIndependentChecker(
            BankFinancingApplication application,
            Authentication authentication
    ) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))
                || authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_MAKER".equals(authority.getAuthority()))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only an independent checker may review financing applications"
            );
        }

        String username = authentication.getName();
        if (sameUsername(username, application.getSubmittedBy())) {
            throw conflict(
                    "Maker-checker violation: submitter cannot review "
                            + "the same application"
            );
        }
    }

    private boolean sameUsername(String first, String second) {
        return first != null && second != null
                && first.trim().equalsIgnoreCase(second.trim());
    }

    private void requireValid(Long applId) {

        BankFinancingApplicationValidationResponse result =
                validationService.validate(applId);

        if (!result.valid()) {
            throw conflict(
                    "Financing application validation failed: "
                            + String.join("; ", result.errors())
            );
        }
    }

    private void validateApprovedTerms(
            BankFinancingApprovalRequest request,
            BankScheme scheme
    ) {
        BigDecimal amount = request.apprAmt();
        Integer tenor = request.apprTenor();

        if (amount == null || amount.signum() <= 0
                || tenor == null || tenor <= 0) {
            throw conflict("Invalid approved amount or tenure");
        }

        if (scheme.getMinAmt() != null
                && amount.compareTo(scheme.getMinAmt()) < 0) {
            throw conflict("Approved amount is below scheme minimum");
        }

        if (scheme.getMaxAmt() != null
                && amount.compareTo(scheme.getMaxAmt()) > 0) {
            throw conflict("Approved amount exceeds scheme maximum");
        }

        if (scheme.getMinTenor() != null
                && tenor < scheme.getMinTenor()) {
            throw conflict("Approved tenure is below scheme minimum");
        }

        if (scheme.getMaxTenor() != null
                && tenor > scheme.getMaxTenor()) {
            throw conflict("Approved tenure exceeds scheme maximum");
        }
    }

    private void validateApprovedRate(BankFinancingApplication application, BigDecimal approvedRate) {
        BankFinancingPricingPolicy.RatePolicy policy =
                BankFinancingPricingPolicy.resolve(jdbcTemplate, application.getSchmId(), LocalDate.now());
        String originType = application.getRateTyp() == null ? "FIXED" : application.getRateTyp();
        if (!originType.equals(policy.rateType())) {
            throw conflict("Scheme pricing type changed since application creation; create a new application");
        }
        BankFinancingPricingPolicy.validateSelected(policy, approvedRate);
    }

    private void saveHistory(
            Long applId,
            String oldStatus,
            String newStatus,
            String actionType,
            String remarks,
            String username
    ) {
        BankFinancingApplicationHistory history =
                new BankFinancingApplicationHistory();

        history.setApplId(applId);
        history.setOldSts(oldStatus);
        history.setNewSts(newStatus);
        history.setActionTyp(actionType);
        history.setRemarks(remarks);
        history.setActionBy(username);

        historyRepository.save(history);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
