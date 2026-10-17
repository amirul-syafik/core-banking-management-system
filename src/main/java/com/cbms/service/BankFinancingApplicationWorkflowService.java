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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class BankFinancingApplicationWorkflowService {

    private final BankFinancingApplicationRepository applicationRepository;
    private final BankFinancingApplicationHistoryRepository historyRepository;
    private final BankFinancingApplicationValidationService validationService;
    private final BankSchemeRepository schemeRepository;
    private final JdbcTemplate jdbcTemplate;

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

        return applicationRepository.saveAndFlush(application);
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
        requireIndependentChecker(application, username);
        requireValid(application.getApplId());

        BankScheme scheme = schemeRepository
                .findById(application.getSchmId())
                .orElseThrow(() -> conflict("Financing scheme not found"));

        validateApprovedTerms(request, scheme);
        validateApprovedRate(application.getSchmId(), request.apprRate());

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

        return applicationRepository.saveAndFlush(application);
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
        requireIndependentChecker(application, username);

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

        return applicationRepository.saveAndFlush(application);
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
            String username
    ) {
        if (Objects.equals(username, application.getCrtBy())
                || Objects.equals(username, application.getSubmittedBy())) {
            throw conflict(
                    "Maker-checker violation: creator or submitter "
                            + "cannot review the same application"
            );
        }
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

    private void validateApprovedRate(
            Long schmId,
            BigDecimal approvedRate
    ) {
        if (approvedRate == null) {
            throw conflict("Approved rate is required");
        }

        List<BigDecimal> rates = jdbcTemplate.query(
                """
                SELECT fixed_rate_pct
                FROM public.bank_scheme_rate
                WHERE schm_id = ?
                  AND rate_typ = 'FIXED'
                  AND rate_sts = 'ACTIVE'
                  AND eff_dt <= ?
                ORDER BY eff_dt DESC, schm_rate_id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> rs.getBigDecimal("fixed_rate_pct"),
                schmId,
                LocalDate.now()
        );

        if (rates.isEmpty() || rates.get(0) == null) {
            throw conflict(
                    "No effective FIXED rate configured for this scheme"
            );
        }

        if (approvedRate.compareTo(rates.get(0)) != 0) {
            throw conflict(
                    "Approved rate must match the effective scheme rate: "
                            + rates.get(0)
            );
        }
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
