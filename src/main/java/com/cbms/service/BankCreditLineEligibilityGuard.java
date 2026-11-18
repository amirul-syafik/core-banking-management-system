package com.cbms.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class BankCreditLineEligibilityGuard {

    private final JdbcTemplate jdbc;
    private final BankCreditLineFloatingRateService floatingRates;

    public BankCreditLineEligibilityGuard(JdbcTemplate jdbc,
                                          BankCreditLineFloatingRateService floatingRates) {
        this.jdbc = jdbc;
        this.floatingRates = floatingRates;
    }

    public void validateForActivation(
            Map<String, Object> facility,
            LocalDate today
    ) {
        validateCustomerAndKyc(facility, today);
        BankFinancialCustomerEligibility.requireActiveOperatingAccount(
                jdbc, number(facility, "cust_id"));
        validateProductAndScheme(facility);
        validateFacilityTerms(facility);
        validateRate(facility, today);
    }

    public void validateForLimitAdjustment(
            Map<String, Object> facility,
            LocalDate today
    ) {
        validateCustomerAndKyc(facility, today);
        BankFinancialCustomerEligibility.requireActiveOperatingAccount(
                jdbc, number(facility, "cust_id"));
        validateProductAndScheme(facility);
        validateFacilityTerms(facility);
        validateRate(facility, today);
    }

    private void validateCustomerAndKyc(
            Map<String, Object> facility,
            LocalDate today
    ) {
        Long custId = number(facility, "cust_id");

        List<String> statuses = jdbc.queryForList(
                """
                        SELECT sts
                        FROM public.customer_master
                        WHERE cust_id = ?
                        """,
                String.class,
                custId
        );

        if (statuses.isEmpty()) {
            throw conflict("Customer not found");
        }

        if (!"ACTIVE".equals(statuses.get(0))) {
            throw conflict("Customer must be ACTIVE");
        }

        List<Map<String, Object>> kycRecords = jdbc.queryForList(
                """
                        SELECT asmt_sts, rev_dt, nxt_rev_dt
                        FROM public.customer_kyc
                        WHERE cust_id = ?
                        ORDER BY upd_dt DESC NULLS LAST, kyc_id DESC
                        LIMIT 1
                        """,
                custId
        );

        if (kycRecords.isEmpty()) {
            throw conflict("Customer has no KYC assessment");
        }

        Map<String, Object> kyc = kycRecords.get(0);

        if (!"VERIFIED".equals(kyc.get("asmt_sts"))) {
            throw conflict("Latest KYC assessment must be VERIFIED");
        }

        LocalDate reviewDate = date(kyc, "rev_dt");
        LocalDate nextReviewDate = date(kyc, "nxt_rev_dt");

        if (reviewDate == null || reviewDate.isAfter(today)) {
            throw conflict("KYC review date is invalid");
        }

        if (nextReviewDate == null || nextReviewDate.isBefore(today)) {
            throw conflict("KYC review has expired");
        }

        Integer identificationCount = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.customer_identification
                        WHERE cust_id = ?
                          AND doc_no IS NOT NULL
                          AND TRIM(doc_no) <> ''
                          AND (exp_dt IS NULL OR exp_dt >= ?)
                        """,
                Integer.class,
                custId,
                today
        );

        if (identificationCount == null || identificationCount == 0) {
            throw conflict(
                    "Customer has no valid identification document"
            );
        }
    }

    private void validateProductAndScheme(
            Map<String, Object> facility
    ) {
        Long prodId = number(facility, "prod_id");
        Long schmId = number(facility, "schm_id");

        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        SELECT
                            p.prod_sts,
                            p.prod_cat,
                            p.banking_typ,
                            p.ccy_cd,
                            s.schm_sts,
                            s.min_amt,
                            s.max_amt
                        FROM public.bank_product p
                        JOIN public.bank_scheme s
                            ON s.prod_id = p.prod_id
                        WHERE p.prod_id = ?
                          AND s.schm_id = ?
                        """,
                prodId,
                schmId
        );

        if (rows.size() != 1) {
            throw conflict("Product and scheme linkage is invalid");
        }

        Map<String, Object> row = rows.get(0);

        if (!"ACTIVE".equals(row.get("prod_sts"))
                || !"ACTIVE".equals(row.get("schm_sts"))) {
            throw conflict("Product and scheme must be ACTIVE");
        }

        String facilityType = (String) facility.get("cl_fac_typ");

        String expectedCategory = switch (facilityType) {
            case "REVOLVING_CREDIT" -> "CREDIT_LINE";
            case "FLOOR_STOCKING" -> "FLOOR_STOCKING";
            default -> throw conflict("Unsupported credit line facility type");
        };

        if (!expectedCategory.equals(row.get("prod_cat"))) {
            throw conflict("Product category does not match facility type");
        }

        if (!"CONVENTIONAL".equals(row.get("banking_typ"))) {
            throw conflict("Only CONVENTIONAL credit lines are supported");
        }

        if (!"MYR".equals(row.get("ccy_cd"))
                || !"MYR".equals(facility.get("ccy_cd"))) {
            throw conflict("Only MYR credit lines are supported");
        }

        BigDecimal approvedLimit = amount(facility, "appr_limit");
        BigDecimal minAmount = amount(row, "min_amt");
        BigDecimal maxAmount = amount(row, "max_amt");

        if (minAmount != null && approvedLimit.compareTo(minAmount) < 0) {
            throw conflict("Approved limit is below scheme minimum");
        }

        if (maxAmount != null && approvedLimit.compareTo(maxAmount) > 0) {
            throw conflict("Approved limit exceeds scheme maximum");
        }
    }

    private void validateFacilityTerms(Map<String, Object> facility) {
        BigDecimal approvedLimit = amount(facility, "appr_limit");
        BigDecimal utilizedAmount = amount(facility, "utilized_amt");

        if (approvedLimit == null || approvedLimit.signum() <= 0) {
            throw conflict("Approved limit must be positive");
        }

        if (utilizedAmount == null
                || utilizedAmount.signum() < 0
                || utilizedAmount.compareTo(approvedLimit) > 0) {
            throw conflict("Facility utilization is invalid");
        }
    }

    private void validateRate(Map<String, Object> facility, LocalDate today) {
        String rateType = (String) facility.get("rate_typ");
        BigDecimal contractedRate = amount(facility, "eff_rate");
        if (contractedRate == null || contractedRate.signum() < 0) {
            throw conflict("Missing contractual credit-line rate");
        }
        if (!"FIXED".equals(rateType) && !"FLOATING".equals(rateType)) {
            throw conflict("Unsupported credit line rate type");
        }

        // Scheme terms are validated against the ORIGINAL effective date.
        // Changing today's scheme price must not change an existing contract.
        LocalDate contractDate = date(facility, "eff_dt");
        List<Map<String, Object>> versions = jdbc.queryForList(
                """
                        SELECT rate_typ, fixed_rate_pct, min_rate_pct, max_rate_pct,
                               base_rate_cd, margin_pct
                        FROM public.bank_scheme_rate
                        WHERE schm_id = ? AND rate_sts = 'ACTIVE' AND eff_dt <= ?
                        ORDER BY eff_dt DESC, schm_rate_id DESC LIMIT 1
                        """,
                number(facility, "schm_id"), contractDate);
        if (versions.isEmpty() || !rateType.equals(versions.get(0).get("rate_typ"))) {
            throw conflict("Original scheme rate is unavailable or incompatible");
        }
        Map<String, Object> original = versions.get(0);
        if ("FIXED".equals(rateType)) {
            BigDecimal fixed = (BigDecimal) original.get("fixed_rate_pct");
            BigDecimal min = (BigDecimal) original.get("min_rate_pct");
            BigDecimal max = (BigDecimal) original.get("max_rate_pct");
            if (fixed == null || contractedRate.compareTo(min == null ? fixed : min) < 0
                    || contractedRate.compareTo(max == null ? fixed : max) > 0) {
                throw conflict("Fixed contractual rate is outside original scheme bounds");
            }
            return;
        }

        String baseCode = (String) facility.get("base_rate_cd");
        BigDecimal margin = amount(facility, "margin_pct");
        if (baseCode == null || baseCode.isBlank() || margin == null) {
            throw conflict("Floating credit line is missing its contracted rate snapshot");
        }
        if (!baseCode.equals(original.get("base_rate_cd"))
                || original.get("margin_pct") == null
                || margin.compareTo((BigDecimal) original.get("margin_pct")) != 0) {
            throw conflict("Floating contract reference differs from original scheme terms");
        }
        // The origination effective rate must match the snapshotted reference.
        if (floatingRates.rateOn(baseCode, margin, contractDate).compareTo(contractedRate) != 0) {
            throw conflict("Floating credit line origination rate does not match contract");
        }
        // The CURRENT reference must exist; it need not equal the origination rate.
        floatingRates.rateOn(baseCode, margin, today);
    }

    private Long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private BigDecimal amount(Map<String, Object> row, String key) {
        return (BigDecimal) row.get(key);
    }

    private LocalDate date(Map<String, Object> row, String key) {
        Object value = row.get(key);

        if (value == null) {
            return null;
        }

        if (value instanceof LocalDate localDate) {
            return localDate;
        }

        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }

        throw new IllegalStateException(
                "Unexpected date type for " + key
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}
