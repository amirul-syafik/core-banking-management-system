package com.cbms.service;

import com.cbms.dto.BankFinancingApplicationValidationResponse;
import com.cbms.entity.BankFinancingApplication;
import com.cbms.entity.BankProduct;
import com.cbms.entity.BankScheme;
import com.cbms.repository.BankFinancingApplicationRepository;
import com.cbms.repository.BankProductRepository;
import com.cbms.repository.BankSchemeRepository;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class BankFinancingApplicationValidationService {

    private static final Set<String> FINANCING_TYPES = Set.of(
            "HIRE_PURCHASE",
            "PERSONAL_FINANCING",
            "TERM_LOAN",
            "CREDIT_LINE",
            "FLOOR_STOCKING"
    );

    private final BankFinancingApplicationRepository applicationRepository;
    private final BankProductRepository productRepository;
    private final BankSchemeRepository schemeRepository;
    private final JdbcTemplate jdbcTemplate;

    public BankFinancingApplicationValidationService(
            BankFinancingApplicationRepository applicationRepository,
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
    public BankFinancingApplicationValidationResponse validate(Long applId) {

        BankFinancingApplication application =
                applicationRepository.findById(applId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Financing application not found"
                        ));

        List<String> errors = new ArrayList<>();

        validateCustomer(application.getCustId(), errors);
        validateKyc(application.getCustId(), errors);
        validateIdentification(application.getCustId(), errors);
        validateProductAndScheme(application, errors);
        validateRate(application.getSchmId(), errors);

        return new BankFinancingApplicationValidationResponse(
                application.getApplId(),
                application.getApplRef(),
                errors.isEmpty(),
                errors
        );
    }

    private void validateCustomer(Long custId, List<String> errors) {

        List<String> statuses = jdbcTemplate.queryForList(
                """
                        SELECT sts
                        FROM public.customer_master
                        WHERE cust_id = ?
                        """,
                String.class,
                custId
        );

        if (statuses.isEmpty()) {
            errors.add("Customer not found");
            return;
        }

        if (!"ACTIVE".equals(statuses.get(0))) {
            errors.add("Customer must be ACTIVE");
        }
    }

    private void validateKyc(Long custId, List<String> errors) {

        List<KycRecord> records = jdbcTemplate.query(
                """
                        SELECT asmt_sts, rev_dt, nxt_rev_dt
                        FROM public.customer_kyc
                        WHERE cust_id = ?
                        ORDER BY upd_dt DESC NULLS LAST, kyc_id DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> new KycRecord(
                        rs.getString("asmt_sts"),
                        rs.getObject("rev_dt", LocalDate.class),
                        rs.getObject("nxt_rev_dt", LocalDate.class)
                ),
                custId
        );

        if (records.isEmpty()) {
            errors.add("Customer has no KYC assessment");
            return;
        }

        KycRecord kyc = records.get(0);

        if (!"VERIFIED".equals(kyc.status())) {
            errors.add("Latest KYC assessment must be VERIFIED");
        }

        LocalDate today = LocalDate.now();

        if (kyc.reviewDate() == null
                || kyc.reviewDate().isAfter(today)) {
            errors.add("KYC review date is invalid");
        }

        if (kyc.nextReviewDate() == null
                || kyc.nextReviewDate().isBefore(today)) {
            errors.add("KYC review has expired or next review date is missing");
        }
    }

    private void validateIdentification(
            Long custId,
            List<String> errors
    ) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.customer_identification
                        WHERE cust_id = ?
                          AND doc_no IS NOT NULL
                          AND TRIM(doc_no) <> ''
                          AND (exp_dt IS NULL OR exp_dt >= CURRENT_DATE)
                        """,
                Integer.class,
                custId
        );

        if (count == null || count == 0) {
            errors.add("Customer has no valid identification document");
        }
    }

    private void validateProductAndScheme(
            BankFinancingApplication application,
            List<String> errors
    ) {
        BankProduct product = productRepository
                .findById(application.getProdId())
                .orElse(null);

        BankScheme scheme = schemeRepository
                .findById(application.getSchmId())
                .orElse(null);

        if (product == null) {
            errors.add("Financing product not found");
        } else {
            if (!"ACTIVE".equals(product.getProdSts())) {
                errors.add("Financing product is not ACTIVE");
            }

            String category = normalize(product.getProdCat());

            if (!FINANCING_TYPES.contains(category)) {
                errors.add("Product is not a supported financing product");
            }

            if (!category.equals(application.getFacTyp())) {
                errors.add("Application financing type does not match product");
            }

            String bankingType = normalize(product.getBankingTyp());

            if (!Set.of("ISLAMIC", "CONVENTIONAL")
                    .contains(bankingType)) {
                errors.add("Unsupported banking type");
            }

            if (!bankingType.equals(application.getBankingTyp())) {
                errors.add("Application banking type does not match product");
            }

            if (!"MYR".equalsIgnoreCase(product.getCcyCd())) {
                errors.add("Only MYR financing is currently supported");
            }

            if (!normalize(product.getCcyCd())
                    .equals(normalize(application.getCcyCd()))) {
                errors.add("Application currency does not match product");
            }
        }

        if (scheme == null) {
            errors.add("Financing scheme not found");
            return;
        }

        if (!"ACTIVE".equals(scheme.getSchmSts())) {
            errors.add("Financing scheme is not ACTIVE");
        }

        if (!application.getProdId().equals(scheme.getProdId())) {
            errors.add("Financing scheme does not belong to product");
        }

        BigDecimal amount = application.getReqAmt();
        Integer tenor = application.getReqTenor();

        if (amount == null || amount.signum() <= 0) {
            errors.add("Requested financing amount must be positive");
        } else {
            if (scheme.getMinAmt() != null
                    && amount.compareTo(scheme.getMinAmt()) < 0) {
                errors.add("Requested amount is below scheme minimum");
            }

            if (scheme.getMaxAmt() != null
                    && amount.compareTo(scheme.getMaxAmt()) > 0) {
                errors.add("Requested amount exceeds scheme maximum");
            }
        }

        if (tenor == null || tenor <= 0) {
            errors.add("Requested tenure must be positive");
        } else {
            if (scheme.getMinTenor() != null
                    && tenor < scheme.getMinTenor()) {
                errors.add("Requested tenure is below scheme minimum");
            }

            if (scheme.getMaxTenor() != null
                    && tenor > scheme.getMaxTenor()) {
                errors.add("Requested tenure exceeds scheme maximum");
            }
        }
    }

    private void validateRate(Long schmId, List<String> errors) {

        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.bank_scheme_rate
                        WHERE schm_id = ?
                          AND rate_sts = 'ACTIVE'
                          AND eff_dt <= CURRENT_DATE
                          AND rate_typ IN ('FIXED', 'FLOATING')
                        """,
                Integer.class,
                schmId
        );

        if (count == null || count == 0) {
            errors.add("No effective financing rate configured for scheme");
        }
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private record KycRecord(
            String status,
            LocalDate reviewDate,
            LocalDate nextReviewDate
    ) {
    }
}
