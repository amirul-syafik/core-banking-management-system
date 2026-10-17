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

    public BankCreditLineEligibilityGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void validateForActivation(
            Map<String, Object> facility,
            LocalDate today
    ) {
        validateCustomerAndKyc(facility, today);
        validateProductAndScheme(facility);
        validateFacilityTerms(facility);
        validateRate(facility, today);
    }

    public void validateForLimitAdjustment(
            Map<String, Object> facility,
            LocalDate today
    ) {
        validateCustomerAndKyc(facility, today);
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

    private void validateRate(
            Map<String, Object> facility,
            LocalDate today
    ) {
        Long schmId = number(facility, "schm_id");
        String rateType = (String) facility.get("rate_typ");
        BigDecimal effectiveRate = amount(facility, "eff_rate");

        if (!"FIXED".equals(rateType) && !"FLOATING".equals(rateType)) {
            throw conflict("Unsupported credit line rate type");
        }

        if (effectiveRate == null || effectiveRate.signum() < 0) {
            throw conflict("Invalid effective rate");
        }

        Integer validRate = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.bank_scheme_rate sr
                WHERE sr.schm_id = ?
                  AND sr.rate_typ = ?
                  AND sr.rate_sts = 'ACTIVE'
                  AND sr.eff_dt <= ?
                  AND (
                      (
                          sr.rate_typ = 'FIXED'
                          AND sr.fixed_rate_pct = ?
                      )
                      OR
                      (
                          sr.rate_typ = 'FLOATING'
                          AND (
                              COALESCE((
                                  SELECT br.rate_pct
                                  FROM public.bank_base_rate br
                                  WHERE br.rate_cd = sr.base_rate_cd
                                    AND br.rate_sts = 'ACTIVE'
                                    AND br.eff_dt <= ?
                                  ORDER BY
                                      br.eff_dt DESC,
                                      br.base_rate_id DESC
                                  LIMIT 1
                              ), -999999) + sr.margin_pct
                          ) = ?
                      )
                  )
                """,
                Integer.class,
                schmId,
                rateType,
                today,
                effectiveRate,
                today,
                effectiveRate
        );

        if (validRate == null || validRate == 0) {
            throw conflict(
                    "No matching effective scheme rate is configured"
            );
        }
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
