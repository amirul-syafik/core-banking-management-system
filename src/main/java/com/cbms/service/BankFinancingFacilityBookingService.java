package com.cbms.service;

import com.cbms.dto.BankFinancingApplicationValidationResponse;
import com.cbms.entity.BankFinancingApplication;
import com.cbms.entity.BankFinancingApplicationHistory;
import com.cbms.entity.BankFinancingFacility;

import com.cbms.repository.BankFinancingApplicationRepository;
import com.cbms.repository.BankFinancingApplicationHistoryRepository;
import com.cbms.repository.BankFinancingFacilityRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class BankFinancingFacilityBookingService {

    private final BankFinancingApplicationRepository applicationRepository;
    private final BankFinancingFacilityRepository facilityRepository;
    private final BankFinancingApplicationHistoryRepository historyRepository;
    private final BankFinancingApplicationValidationService validationService;

    public BankFinancingFacilityBookingService(
            BankFinancingApplicationRepository applicationRepository,
            BankFinancingFacilityRepository facilityRepository,
            BankFinancingApplicationHistoryRepository historyRepository,
            BankFinancingApplicationValidationService validationService
    ) {
        this.applicationRepository = applicationRepository;
        this.facilityRepository = facilityRepository;
        this.historyRepository = historyRepository;
        this.validationService = validationService;
    }

    @Transactional
    public BankFinancingFacility book(
            Long applId,
            Authentication authentication
    ) {
        String username = authentication.getName();

        // Lock the application throughout the booking transaction.
        BankFinancingApplication application =
                applicationRepository.findByIdForUpdate(applId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Financing application not found"
                        ));

        // Idempotent retry: return the previously booked facility.
        if ("COMPLETED".equals(application.getApplSts())) {

            if (application.getFacId() == null) {
                throw conflict(
                        "Completed application has no linked facility"
                );
            }

            BankFinancingFacility existing =
                    facilityRepository.findById(application.getFacId())
                            .orElseThrow(() -> conflict(
                                    "Linked financing facility not found"
                            ));

            if (!applId.equals(existing.getApplId())) {
                throw conflict(
                        "Facility does not belong to this application"
                );
            }

            return existing;
        }

        if (!"APPROVED".equals(application.getApplSts())) {
            throw conflict(
                    "Only APPROVED financing applications can be booked"
            );
        }

        if (application.getFacId() != null) {
            throw conflict(
                    "Application already references a financing facility"
            );
        }

        if (facilityRepository.findByApplId(applId).isPresent()) {
            throw conflict(
                    "A financing facility already exists for this application"
            );
        }

        // Revalidate customer, KYC, product, scheme and rate.
        BankFinancingApplicationValidationResponse validation =
                validationService.validate(applId);

        if (!validation.valid()) {
            throw conflict(
                    "Financing validation failed: "
                            + String.join("; ", validation.errors())
            );
        }

        if (application.getApprAmt() == null
                || application.getApprAmt().signum() <= 0
                || application.getApprTenor() == null
                || application.getApprTenor() <= 0
                || application.getApprRate() == null) {

            throw conflict(
                    "Approved financing terms are incomplete"
            );
        }

        OffsetDateTime now = OffsetDateTime.now();

        BankFinancingFacility facility =
                new BankFinancingFacility();

        facility.setApplId(application.getApplId());

        facility.setFacRef(
                "FAC-" + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .toUpperCase()
        );

        facility.setCustId(application.getCustId());
        facility.setProdId(application.getProdId());
        facility.setSchmId(application.getSchmId());

        facility.setFacTyp(application.getFacTyp());
        facility.setCcyCd(application.getCcyCd());

        facility.setApprAmt(application.getApprAmt());
        facility.setTenorMth(application.getApprTenor());

        facility.setRateTyp("FIXED");
        facility.setEffRate(application.getApprRate());

        // Booking is not activation or disbursement.
        facility.setFacSts("DRAFT");

        facility.setApprDt(
                application.getReviewedDt() == null
                        ? null
                        : application.getReviewedDt().toLocalDate()
        );

        facility.setBookDt(now.toLocalDate());

        // Maturity date will be determined by the
        // contractual repayment schedule
        facility.setMaturityDt(null);

        facility.setRemarks(
                "Booked from financing application "
                        + application.getApplRef()
        );

        facility.setCrtBy(username);
        facility.setUpdBy(username);

        BankFinancingFacility savedFacility =
                facilityRepository.saveAndFlush(facility);

        application.setFacId(savedFacility.getFacId());
        application.setApplSts("COMPLETED");
        application.setUpdBy(username);

        applicationRepository.saveAndFlush(application);

        BankFinancingApplicationHistory history =
                new BankFinancingApplicationHistory();

        history.setApplId(application.getApplId());
        history.setOldSts("APPROVED");
        history.setNewSts("COMPLETED");
        history.setActionTyp("BOOK_FACILITY");

        history.setRemarks(
                "Financing facility created: "
                        + savedFacility.getFacRef()
        );

        history.setActionBy(username);

        historyRepository.saveAndFlush(history);

        return savedFacility;
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}
