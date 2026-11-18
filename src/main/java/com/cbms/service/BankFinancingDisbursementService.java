package com.cbms.service;

import com.cbms.reference.BusinessReferenceGenerator;

import com.cbms.dto.BankFinancingDisbursementRequest;
import com.cbms.dto.BankFinancingDisbursementResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class BankFinancingDisbursementService {

    private static final String GL_HP_RECEIVABLE = "HP_PRINCIPAL_RECEIVABLE";
    private static final String GL_DISB_CLEARING = "DISBURSEMENT_CLEARING";

    private final JdbcTemplate jdbcTemplate;
    private final BankFinancingFloatingRepricingService floatingRepricing;

    @Autowired
    public BankFinancingDisbursementService(JdbcTemplate jdbcTemplate,
            BankFinancingFloatingRepricingService floatingRepricing) {
        this.jdbcTemplate = jdbcTemplate;
        this.floatingRepricing = floatingRepricing;
    }

    public BankFinancingDisbursementService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null);
    }

    @Transactional
    public BankFinancingDisbursementResponse disburse(
            Long facId,
            BankFinancingDisbursementRequest request,
            Authentication authentication
    ) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (facId == null || facId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid financing facility ID");
        }

        String username = authentication.getName();

        // Serialize all disbursement attempts for this facility.
        List<Map<String, Object>> facilities = jdbcTemplate.queryForList(
                """
                        SELECT fac_id, appl_id, fac_typ, fac_sts,
                               appr_amt, tenor_mth, maturity_dt,
                               rate_typ, ccy_cd
                        FROM public.bank_financing_facility
                        WHERE fac_id = ?
                        FOR UPDATE
                        """,
                facId
        );

        if (facilities.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Financing facility not found"
            );
        }

        Map<String, Object> facility = facilities.get(0);

        // Retry must use exactly the same request details.
        List<Map<String, Object>> previous = jdbcTemplate.queryForList(
                """
                        SELECT *
                        FROM public.bank_financing_disbursement
                        WHERE fac_id = ?
                        """,
                facId
        );

        if (!previous.isEmpty()) {
            Map<String, Object> existing = previous.get(0);

            boolean sameRequest =
                    Objects.equals(
                            existing.get("idempotency_key"),
                            request.idempotencyKey()
                    )
                            && ((BigDecimal) existing.get("disb_amt"))
                            .compareTo(request.amount()) == 0
                            && Objects.equals(
                            existing.get("beneficiary_name"),
                            request.beneficiaryName()
                    )
                            && Objects.equals(
                            existing.get("beneficiary_ref"),
                            request.beneficiaryRef()
                    );

            if (!sameRequest) {
                throw conflict(
                        "Facility already has a different disbursement"
                );
            }

            return mapResponse(existing);
        }

        // Only new disbursements require an ACTIVE customer. Existing successful
        // idempotent retries return above, even if the customer status changed.
        Integer activeCustomer = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.bank_financing_application a
                        JOIN public.customer_master c ON c.cust_id = a.cust_id
                        WHERE a.appl_id = ?
                          AND c.sts = 'ACTIVE'
                        """,
                Integer.class,
                facility.get("appl_id")
        );
        if (activeCustomer == null || activeCustomer != 1) {
            throw conflict("Financing customer must be ACTIVE before disbursement");
        }

        if (!"DRAFT".equals(facility.get("fac_sts"))) {
            throw conflict("Facility must be DRAFT before disbursement");
        }

        if (facility.get("appl_id") == null) {
            throw conflict(
                    "Facility must originate from an approved application"
            );
        }

        // Disbursement is the first financial execution point. Booked DRAFTs may
        // exist during onboarding, but cannot disburse without current KYC and
        // a suitable active account. Idempotent retries have already returned.
        Long customerId = jdbcTemplate.queryForObject(
                "SELECT cust_id FROM public.bank_financing_application WHERE appl_id = ?",
                Long.class, facility.get("appl_id"));
        BankFinancialCustomerEligibility.requireReadyForNewExposure(jdbcTemplate, customerId);
        BankFinancialCustomerEligibility.requireActiveOperatingAccount(jdbcTemplate, customerId);

        if (!"HIRE_PURCHASE".equals(facility.get("fac_typ"))) {
            throw conflict("Only HIRE_PURCHASE is supported");
        }

        Map<String, Object> securitySummary = jdbcTemplate.queryForMap(
                """
                        SELECT COUNT(*) AS active_count,
                               COUNT(*) FILTER (WHERE
                                   c.collateral_sts = 'REGISTERED'
                                   AND c.owner_cust_id = a.cust_id
                                   AND EXISTS (
                                       SELECT 1 FROM public.bank_collateral_valuation val
                                       WHERE val.collateral_id = c.collateral_id
                                         AND val.currency_cd = f.ccy_cd
                                         AND val.forced_sale_value > 0
                                       ORDER BY val.valuation_dt DESC, val.valuation_id DESC
                                       LIMIT 1
                                   )
                                   AND (c.collateral_typ <> 'VEHICLE' OR
                                       (c.vehicle_id IS NOT NULL
                                        AND v.vehicle_sts = 'ACTIVE'
                                        AND v.owner_cust_id = c.owner_cust_id))
                               ) AS valid_count
                        FROM public.bank_financing_collateral l
                        JOIN public.bank_collateral c ON c.collateral_id = l.collateral_id
                        LEFT JOIN public.bank_vehicle v ON v.vehicle_id = c.vehicle_id
                        JOIN public.bank_financing_application a ON a.appl_id = l.appl_id
                        JOIN public.bank_financing_facility f ON f.fac_id = l.fac_id
                        WHERE l.appl_id = ? AND l.fac_id = ? AND l.link_sts = 'ACTIVE'
                        """,
                facility.get("appl_id"), facId);
        long activeSecurityCount = ((Number) securitySummary.get("active_count")).longValue();
        long validSecurityCount = ((Number) securitySummary.get("valid_count")).longValue();
        if (activeSecurityCount == 0 || validSecurityCount != activeSecurityCount)
            throw conflict("Hire Purchase requires at least one active, valued, complete collateral assignment before disbursement");

        if (!"FIXED".equals(facility.get("rate_typ"))
                && !"FLOATING".equals(facility.get("rate_typ"))) {
            throw conflict("Unsupported financing rate type");
        }
        if ("FLOATING".equals(facility.get("rate_typ"))) {
            if (floatingRepricing == null) throw conflict("Floating pricing service is unavailable");
            // Reprice before disbursement if the reference changed after scheduling.
            floatingRepricing.reprice(facId, java.time.LocalDate.now(), username);
        }

        if (!"MYR".equals(facility.get("ccy_cd"))) {
            throw conflict("Only MYR disbursement is supported");
        }

        Integer conventional = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.bank_financing_facility f
                        JOIN public.bank_product p ON p.prod_id = f.prod_id
                        WHERE f.fac_id = ?
                          AND p.banking_typ = 'CONVENTIONAL'
                        """,
                Integer.class,
                facId
        );

        if (conventional == null || conventional != 1) {
            throw conflict("Only conventional financing is supported");
        }

        Integer approvedApplication = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.bank_financing_application
                        WHERE appl_id = ?
                          AND fac_id = ?
                          AND appl_sts = 'COMPLETED'
                        """,
                Integer.class,
                facility.get("appl_id"),
                facId
        );

        if (approvedApplication == null || approvedApplication != 1) {
            throw conflict("Facility application linkage is invalid");
        }

        BigDecimal approvedAmount =
                (BigDecimal) facility.get("appr_amt");

        if (approvedAmount == null
                || request.amount().compareTo(approvedAmount) != 0) {
            throw conflict(
                    "Disbursement amount must equal approved facility amount"
            );
        }

        if (facility.get("maturity_dt") == null) {
            throw conflict("Repayment schedule has not been generated");
        }

        Integer expectedTenor = (Integer) facility.get("tenor_mth");

        Map<String, Object> scheduleSummary =
                jdbcTemplate.queryForMap(
                        """
                                SELECT COUNT(*) AS inst_count,
                                       COALESCE(SUM(principal_amt), 0)
                                           AS principal_total,
                                       COALESCE(SUM(paid_principal), 0)
                                           AS paid_principal_total,
                                       COALESCE(SUM(paid_interest), 0)
                                           AS paid_interest_total
                                FROM public.bank_financing_repayment_schedule
                                WHERE fac_id = ?
                                """,
                        facId
                );

        long instalmentCount =
                ((Number) scheduleSummary.get("inst_count")).longValue();

        BigDecimal schedulePrincipal =
                (BigDecimal) scheduleSummary.get("principal_total");

        if (expectedTenor == null
                || instalmentCount != expectedTenor
                || schedulePrincipal.compareTo(approvedAmount) != 0) {
            throw conflict(
                    "Repayment schedule does not match approved facility terms"
            );
        }

        if (((BigDecimal) scheduleSummary.get("paid_principal_total"))
                .signum() != 0
                || ((BigDecimal) scheduleSummary.get("paid_interest_total"))
                .signum() != 0) {
            throw conflict("Repayment schedule already contains payments");
        }

        // Check idempotency key across all facilities.
        Integer keyCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.bank_financing_disbursement
                        WHERE idempotency_key = ?
                        """,
                Integer.class,
                request.idempotencyKey()
        );

        if (keyCount != null && keyCount > 0) {
            throw conflict("Idempotency key already used");
        }

        String reference = BusinessReferenceGenerator.next(
                jdbcTemplate, BusinessReferenceGenerator.Stream.FINANCING_DISBURSEMENT);

        BigDecimal amount =
                request.amount().setScale(2, RoundingMode.UNNECESSARY);

        Long disbId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO public.bank_financing_disbursement
                        (
                            fac_id, disb_ref, idempotency_key,
                            disb_amt, disb_sts,
                            beneficiary_name, beneficiary_ref,
                            remarks, crt_by
                        )
                        VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?)
                        RETURNING disb_id
                        """,
                Long.class,
                facId,
                reference,
                request.idempotencyKey(),
                amount,
                request.beneficiaryName(),
                request.beneficiaryRef(),
                request.remarks(),
                username
        );

        // Debit principal receivable.
        insertJournal(
                disbId, 1, GL_HP_RECEIVABLE,
                amount, BigDecimal.ZERO, username
        );

        // Credit disbursement clearing.
        insertJournal(
                disbId, 2, GL_DISB_CLEARING,
                BigDecimal.ZERO, amount, username
        );

        Map<String, Object> journalTotals = jdbcTemplate.queryForMap(
                """
                        SELECT COUNT(*) AS line_count,
                               COALESCE(SUM(dr_amt), 0) AS total_dr,
                               COALESCE(SUM(cr_amt), 0) AS total_cr
                        FROM public.bank_financing_disbursement_journal
                        WHERE disb_id = ?
                        """,
                disbId
        );

        BigDecimal debit = (BigDecimal) journalTotals.get("total_dr");
        BigDecimal credit = (BigDecimal) journalTotals.get("total_cr");

        if (((Number) journalTotals.get("line_count")).intValue() != 2
                || debit.compareTo(credit) != 0
                || debit.compareTo(amount) != 0) {
            throw conflict("Disbursement journal is not balanced");
        }

        jdbcTemplate.update(
                """
                        UPDATE public.bank_financing_disbursement
                        SET disb_sts = 'POSTED',
                            posted_dt = CURRENT_TIMESTAMP,
                            posted_by = ?
                        WHERE disb_id = ?
                        """,
                username,
                disbId
        );

        jdbcTemplate.update(
                """
                        UPDATE public.bank_financing_facility
                        SET fac_sts = 'ACTIVE',
                            upd_dt = CURRENT_TIMESTAMP,
                            upd_by = ?
                        WHERE fac_id = ?
                        """,
                username,
                facId
        );

        return getById(disbId);
    }

    @Transactional(readOnly = true)
    public BankFinancingDisbursementResponse getByFacility(Long facId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        SELECT *
                        FROM public.bank_financing_disbursement
                        WHERE fac_id = ?
                        """,
                facId
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Disbursement not found"
            );
        }

        return mapResponse(rows.get(0));
    }

    private BankFinancingDisbursementResponse getById(Long disbId) {
        return mapResponse(jdbcTemplate.queryForMap(
                """
                        SELECT *
                        FROM public.bank_financing_disbursement
                        WHERE disb_id = ?
                        """,
                disbId
        ));
    }

    private void insertJournal(
            Long disbId,
            int lineNo,
            String glCode,
            BigDecimal debit,
            BigDecimal credit,
            String username
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO public.bank_financing_disbursement_journal
                        (disb_id, line_no, gl_cd, dr_amt, cr_amt, crt_by)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                disbId,
                lineNo,
                glCode,
                debit,
                credit,
                username
        );
    }

    private BankFinancingDisbursementResponse mapResponse(
            Map<String, Object> row
    ) {
        Object postedDate = row.get("posted_dt");

        OffsetDateTime postedDt = null;

        if (postedDate instanceof OffsetDateTime value) {
            postedDt = value;
        } else if (postedDate instanceof java.sql.Timestamp value) {
            postedDt = value.toInstant()
                    .atOffset(java.time.ZoneOffset.UTC);
        }

        return new BankFinancingDisbursementResponse(
                ((Number) row.get("disb_id")).longValue(),
                ((Number) row.get("fac_id")).longValue(),
                (String) row.get("disb_ref"),
                (BigDecimal) row.get("disb_amt"),
                (String) row.get("disb_sts"),
                (String) row.get("beneficiary_name"),
                (String) row.get("beneficiary_ref"),
                postedDt
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}
