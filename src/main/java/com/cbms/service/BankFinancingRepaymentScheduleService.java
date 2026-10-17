package com.cbms.service;

import com.cbms.dto.BankFinancingScheduleGenerateRequest;
import com.cbms.entity.BankFinancingFacility;
import com.cbms.entity.BankFinancingRepaymentSchedule;
import com.cbms.entity.BankProduct;
import com.cbms.repository.BankFinancingFacilityRepository;
import com.cbms.repository.BankFinancingRepaymentScheduleRepository;
import com.cbms.repository.BankProductRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class BankFinancingRepaymentScheduleService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final BankFinancingFacilityRepository facilityRepository;
    private final BankFinancingRepaymentScheduleRepository scheduleRepository;
    private final BankProductRepository productRepository;
    private final EntityManager entityManager;

    public BankFinancingRepaymentScheduleService(
            BankFinancingFacilityRepository facilityRepository,
            BankFinancingRepaymentScheduleRepository scheduleRepository,
            BankProductRepository productRepository,
            EntityManager entityManager
    ) {
        this.facilityRepository = facilityRepository;
        this.scheduleRepository = scheduleRepository;
        this.productRepository = productRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public List<BankFinancingRepaymentSchedule> generate(
            Long facId,
            BankFinancingScheduleGenerateRequest request,
            Authentication authentication
    ) {
        // Lock facility to serialize concurrent generation requests.
        BankFinancingFacility facility = entityManager.find(
                BankFinancingFacility.class,
                facId,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (facility == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Financing facility not found"
            );
        }

        List<BankFinancingRepaymentSchedule> existing =
                scheduleRepository.findByFacIdOrderByInstNoAsc(facId);

        if (!existing.isEmpty()) {
            if (!existing.get(0).getDueDt()
                    .equals(request.firstDueDate())) {
                throw conflict(
                        "Schedule already exists with a different first due date"
                );
            }
            return existing;
        }

        validateFacility(facility);

        if (request.firstDueDate() == null) {
            throw conflict("First due date is required");
        }

        if (facility.getBookDt() == null
                || !request.firstDueDate().isAfter(facility.getBookDt())) {
            throw conflict(
                    "First due date must be after facility booking date"
            );
        }

        BigDecimal principal =
                facility.getApprAmt().setScale(MONEY_SCALE, ROUNDING);

        BigDecimal annualRate = facility.getEffRate();
        int months = facility.getTenorMth();

        // Flat interest:
        // principal * (annual rate / 100) * (months / 12)
        BigDecimal totalInterest = principal
                .multiply(annualRate)
                .multiply(BigDecimal.valueOf(months))
                .divide(BigDecimal.valueOf(1200), MONEY_SCALE, ROUNDING);

        BigDecimal totalRepayment = principal.add(totalInterest);

        BigDecimal regularInstalment = totalRepayment.divide(
                BigDecimal.valueOf(months),
                MONEY_SCALE,
                ROUNDING
        );

        BigDecimal regularInterest = totalInterest.divide(
                BigDecimal.valueOf(months),
                MONEY_SCALE,
                ROUNDING
        );

        BigDecimal remainingPrincipal = principal;
        BigDecimal allocatedInterest = BigDecimal.ZERO;
        BigDecimal allocatedRepayment = BigDecimal.ZERO;

        List<BankFinancingRepaymentSchedule> schedules =
                new ArrayList<>();

        for (int instNo = 1; instNo <= months; instNo++) {

            boolean last = instNo == months;

            BigDecimal interest;
            BigDecimal instalment;
            BigDecimal principalPart;

            if (last) {
                interest = totalInterest.subtract(allocatedInterest);
                instalment = totalRepayment.subtract(allocatedRepayment);
                principalPart = remainingPrincipal;
            } else {
                interest = regularInterest;
                instalment = regularInstalment;
                principalPart = instalment.subtract(interest);
            }

            if (principalPart.signum() < 0
                    || principalPart.compareTo(remainingPrincipal) > 0) {
                throw conflict(
                        "Invalid principal allocation for instalment " + instNo
                );
            }

            BigDecimal closingPrincipal =
                    remainingPrincipal.subtract(principalPart);

            BankFinancingRepaymentSchedule schedule =
                    new BankFinancingRepaymentSchedule();

            schedule.setFacId(facId);
            schedule.setInstNo(instNo);

            // Preserve the requested calendar day where possible.
            schedule.setDueDt(
                    request.firstDueDate().plusMonths(instNo - 1L)
            );

            schedule.setOpeningPrincipal(remainingPrincipal);
            schedule.setPrincipalAmt(principalPart);
            schedule.setInterestAmt(interest);
            schedule.setInstAmt(instalment);
            schedule.setClosingPrincipal(closingPrincipal);

            schedule.setPaidPrincipal(BigDecimal.ZERO);
            schedule.setPaidInterest(BigDecimal.ZERO);
            schedule.setInstSts("PENDING");
            schedule.setCrtBy(authentication.getName());

            schedules.add(schedule);

            remainingPrincipal = closingPrincipal;
            allocatedInterest = allocatedInterest.add(interest);
            allocatedRepayment = allocatedRepayment.add(instalment);
        }

        if (remainingPrincipal.compareTo(BigDecimal.ZERO) != 0
                || allocatedInterest.compareTo(totalInterest) != 0
                || allocatedRepayment.compareTo(totalRepayment) != 0) {
            throw conflict("Repayment schedule totals do not reconcile");
        }

        List<BankFinancingRepaymentSchedule> saved =
                scheduleRepository.saveAllAndFlush(schedules);

        facility.setMaturityDt(
                saved.get(saved.size() - 1).getDueDt()
        );

        facility.setUpdBy(authentication.getName());
        facilityRepository.saveAndFlush(facility);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<BankFinancingRepaymentSchedule> getSchedule(Long facId) {

        if (!facilityRepository.existsById(facId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Financing facility not found"
            );
        }

        return scheduleRepository.findByFacIdOrderByInstNoAsc(facId);
    }

    private void validateFacility(BankFinancingFacility facility) {

        if (!"DRAFT".equals(facility.getFacSts())) {
            throw conflict(
                    "Only DRAFT facilities can have a schedule generated"
            );
        }

        if (facility.getApplId() == null) {
            throw conflict(
                    "Facility must originate from an approved financing application"
            );
        }

        if (!"HIRE_PURCHASE".equals(facility.getFacTyp())) {
            throw conflict(
                    "Only HIRE_PURCHASE is supported in this phase"
            );
        }

        if (!"FIXED".equals(facility.getRateTyp())) {
            throw conflict(
                    "Only FIXED-rate facilities are supported"
            );
        }

        BankProduct product = productRepository
                .findById(facility.getProdId())
                .orElseThrow(() -> conflict("Financing product not found"));

        if (!"CONVENTIONAL".equals(product.getBankingTyp())) {
            throw conflict(
                    "Islamic financing requires a separate profit calculation method"
            );
        }

        if (!"MYR".equals(facility.getCcyCd())) {
            throw conflict("Only MYR is currently supported");
        }

        if (facility.getApprAmt() == null
                || facility.getApprAmt().signum() <= 0
                || facility.getTenorMth() == null
                || facility.getTenorMth() <= 0
                || facility.getEffRate() == null
                || facility.getEffRate().signum() < 0) {
            throw conflict("Facility financing terms are invalid");
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}
