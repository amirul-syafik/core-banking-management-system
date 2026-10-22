package com.cbms.service;

import com.cbms.dto.BankAccountApplicationValidationResponse;
import com.cbms.entity.BankAccountApplication;
import com.cbms.entity.BankProduct;
import com.cbms.entity.BankScheme;
import com.cbms.repository.BankAccountApplicationRepository;
import com.cbms.repository.BankProductRepository;
import com.cbms.repository.BankSchemeRepository;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class BankAccountApplicationValidationService {

    private final BankAccountApplicationRepository applicationRepository;
    private final BankProductRepository productRepository;
    private final BankSchemeRepository schemeRepository;
    private final JdbcTemplate jdbcTemplate;

    public BankAccountApplicationValidationService(
            BankAccountApplicationRepository applicationRepository,
            BankProductRepository productRepository,
            BankSchemeRepository schemeRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.applicationRepository = applicationRepository;
        this.productRepository = productRepository;
        this.schemeRepository = schemeRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public BankAccountApplicationValidationResponse validate(Long applId) {

        BankAccountApplication application =
                applicationRepository.findById(applId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Application not found"
                        ));

        if ("REJECTED".equals(application.getApplSts())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Rejected applications cannot be revalidated");
        }

        List<String> errors = new ArrayList<>();

        // 1. Customer existence
        Integer customerCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.customer_master
                        WHERE cust_id = ?
                        """,
                Integer.class,
                application.getCustId()
        );

        if (customerCount != null && customerCount > 0) {
            validateCustomerEligibility(application.getCustId(), errors);
        }

        if (customerCount == null || customerCount == 0) {
            errors.add("Customer not found");
        }

        // 2. Product validation
        BankProduct product = productRepository
                .findById(application.getProdId())
                .orElse(null);

        if (product == null) {
            errors.add("Product not found");
        } else if (!"ACTIVE".equals(product.getProdSts())) {
            errors.add("Product is not ACTIVE");
        }

        // 3. Scheme validation
        BankScheme scheme = schemeRepository
                .findById(application.getSchmId())
                .orElse(null);

        if (scheme == null) {
            errors.add("Scheme not found");
        } else {
            if (!"ACTIVE".equals(scheme.getSchmSts())) {
                errors.add("Scheme is not ACTIVE");
            }

            if (!scheme.getProdId().equals(application.getProdId())) {
                errors.add("Scheme does not belong to selected product");
            }

            validateAmount(application, scheme, errors);
            validateTenor(application, scheme, errors);
        }

        // 4. Product-specific requirements
        if (product != null) {
            validateProductRequirements(application, product, errors);
        }

        return new BankAccountApplicationValidationResponse(
                application.getApplId(),
                application.getApplRef(),
                errors.isEmpty(),
                List.copyOf(errors)
        );
    }

    private void validateAmount(
            BankAccountApplication application,
            BankScheme scheme,
            List<String> errors
    ) {
        BigDecimal amount = application.getReqAmt();

        if (amount == null) {
            return;
        }

        if (scheme.getMinAmt() != null
                && amount.compareTo(scheme.getMinAmt()) < 0) {
            errors.add("Requested amount is below scheme minimum");
        }

        if (scheme.getMaxAmt() != null
                && amount.compareTo(scheme.getMaxAmt()) > 0) {
            errors.add("Requested amount exceeds scheme maximum");
        }
    }

    private void validateTenor(
            BankAccountApplication application,
            BankScheme scheme,
            List<String> errors
    ) {
        Integer tenor = application.getReqTenor();

        if (tenor == null) {
            return;
        }

        if (scheme.getMinTenor() != null
                && tenor < scheme.getMinTenor()) {
            errors.add("Requested tenure is below scheme minimum");
        }

        if (scheme.getMaxTenor() != null
                && tenor > scheme.getMaxTenor()) {
            errors.add("Requested tenure exceeds scheme maximum");
        }
    }

    private void validateProductRequirements(
            BankAccountApplication application,
            BankProduct product,
            List<String> errors
    ) {
        String category = product.getProdCat();

        boolean financingProduct = switch (category) {
            case "HIRE_PURCHASE",
                 "WHOLESALE_FINANCING",
                 "FLOOR_STOCKING",
                 "CREDIT_LINE" -> true;
            default -> false;
        };

        if (financingProduct) {
            if (application.getReqAmt() == null
                    || application.getReqAmt()
                    .compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(
                        "Financing applications require an amount greater than zero"
                );
            }

            if (application.getReqTenor() == null) {
                errors.add(
                        "Financing applications require a requested tenure"
                );
            }
        }

        if ("FIXED_DEPOSIT".equals(category)) {
            if (application.getReqAmt() == null
                    || application.getReqAmt()
                    .compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(
                        "Fixed deposit applications require a placement amount"
                );
            }

            if (application.getReqTenor() == null) {
                errors.add(
                        "Fixed deposit applications require a placement tenure"
                );
            }
        }
    }


    private void validateCustomerEligibility(
            Long custId,
            List<String> errors
    ) {
        LocalDate today = LocalDate.now();

        // 1. Customer status
        List<String> customerStatuses = jdbcTemplate.queryForList(
                """
                        SELECT sts
                        FROM public.customer_master
                        WHERE cust_id = ?
                        """,
                String.class,
                custId
        );

        if (customerStatuses.isEmpty()
                || !"ACTIVE".equalsIgnoreCase(customerStatuses.get(0))) {
            errors.add("Customer is not ACTIVE");
        }

        // 2. Approved, current KYC assessment
        Integer verifiedKycCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.customer_kyc
                        WHERE cust_id = ?
                          AND asmt_sts = 'VERIFIED'
                          AND rev_dt IS NOT NULL
                          AND rev_dt <= CURRENT_DATE
                          AND nxt_rev_dt IS NOT NULL
                          AND nxt_rev_dt >= CURRENT_DATE
                        """,
                Integer.class,
                custId
        );

        if (verifiedKycCount == null || verifiedKycCount == 0) {
            errors.add(
                    "Customer does not have a current VERIFIED KYC assessment"
            );
        }

        // 3. Valid identification
        Integer identificationCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.customer_identification
                        WHERE cust_id = ?
                          AND doc_no IS NOT NULL
                          AND BTRIM(doc_no) <> ''
                          AND (exp_dt IS NULL OR exp_dt >= ?)
                        """,
                Integer.class,
                custId,
                today
        );

        if (identificationCount == null || identificationCount == 0) {
            errors.add(
                    "Customer does not have valid identification"
            );
        }
    }

}
