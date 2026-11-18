package com.cbms.service;

import com.cbms.dto.BankFixedDepositCreateRequest;
import com.cbms.reference.BusinessReferenceGenerator;
import com.cbms.dto.BankFixedDepositResponse;

import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class BankFixedDepositService {

    private final JdbcTemplate jdbc;
    private final BankFixedDepositMaturityService maturityService;
    private final BankFloatingDepositRateService floatingRates;
    private final BankIslamicDepositDraftService islamicDrafts;

    /** Compatibility for existing direct-instantiation tests. */
    public BankFixedDepositService(JdbcTemplate jdbc, BankFixedDepositMaturityService maturityService) {
        this(jdbc, maturityService, new BankFloatingDepositRateService(jdbc),
                new BankIslamicDepositDraftService(jdbc));
    }

    /** Compatibility for existing tests constructing the floating-rate implementation. */
    public BankFixedDepositService(JdbcTemplate jdbc, BankFixedDepositMaturityService maturityService,
                                   BankFloatingDepositRateService floatingRates) {
        this(jdbc, maturityService, floatingRates, new BankIslamicDepositDraftService(jdbc));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public BankFixedDepositService(JdbcTemplate jdbc, BankFixedDepositMaturityService maturityService,
                                   BankFloatingDepositRateService floatingRates,
                                   BankIslamicDepositDraftService islamicDrafts) {
        this.jdbc = jdbc;
        this.maturityService = maturityService;
        this.floatingRates = floatingRates;
        this.islamicDrafts = islamicDrafts;
    }

    @Transactional
    public BankFixedDepositResponse create(
            BankFixedDepositCreateRequest request,
            Authentication authentication
    ) {
        LocalDate today = LocalDate.now();

        if (!request.placementDate().equals(today)) {
            throw conflict(
                    "Placement date must be today's date"
            );
        }

        // Validate customer
        List<String> customerStatuses = jdbc.queryForList(
                """
                        SELECT sts
                        FROM public.customer_master
                        WHERE cust_id = ?
                        """,
                String.class,
                request.custId()
        );

        if (customerStatuses.isEmpty()) {
            throw notFound("Customer not found");
        }

        if (!"ACTIVE".equals(customerStatuses.get(0))) {
            throw conflict("Customer must be ACTIVE");
        }

        // Validate product
        List<Map<String, Object>> products = jdbc.queryForList(
                """
                        SELECT *
                        FROM public.bank_product
                        WHERE prod_id = ?
                        """,
                request.prodId()
        );

        if (products.isEmpty()) {
            throw notFound("Product not found");
        }

        Map<String, Object> product = products.get(0);

        if (!"ACTIVE".equals(product.get("prod_sts"))) {
            throw conflict("Product must be ACTIVE");
        }

        String category = (String) product.get("prod_cat");

        if (!"FIXED_DEPOSIT".equals(category)
                && !"TERM_DEPOSIT".equals(category)) {
            throw conflict(
                    "Product category must be FIXED_DEPOSIT or TERM_DEPOSIT"
            );
        }

        if (!request.fdType().equals(category)) {
            throw conflict(
                    "Deposit type must match product category"
            );
        }

        if (!"MYR".equals(product.get("ccy_cd"))) {
            throw conflict("Only MYR deposits are supported");
        }

        // Validate scheme
        List<Map<String, Object>> schemes = jdbc.queryForList(
                """
                        SELECT *
                        FROM public.bank_scheme
                        WHERE schm_id = ?
                        """,
                request.schmId()
        );

        if (schemes.isEmpty()) {
            throw notFound("Scheme not found");
        }

        Map<String, Object> scheme = schemes.get(0);

        if (!"ACTIVE".equals(scheme.get("schm_sts"))) {
            throw conflict("Scheme must be ACTIVE");
        }

        if (((Number) scheme.get("prod_id")).longValue()
                != request.prodId()) {
            throw conflict("Scheme does not belong to product");
        }

        BigDecimal minAmount =
                (BigDecimal) scheme.get("min_amt");

        BigDecimal maxAmount =
                (BigDecimal) scheme.get("max_amt");

        if (request.principalAmount().compareTo(minAmount) < 0
                || request.principalAmount().compareTo(maxAmount) > 0) {
            throw conflict(
                    "Deposit amount is outside scheme limits"
            );
        }

        int minTenor =
                ((Number) scheme.get("min_tenor")).intValue();

        int maxTenor =
                ((Number) scheme.get("max_tenor")).intValue();

        if (request.tenorMonths() < minTenor
                || request.tenorMonths() > maxTenor) {
            throw conflict(
                    "Deposit tenure is outside scheme limits"
            );
        }

        // Select only the latest applicable rate version; do not accept an older version
        // simply because it happens to contain the requested value.
        List<Map<String, Object>> rates = jdbc.queryForList(
                """
                        SELECT rate_typ, fixed_rate_pct, min_rate_pct, max_rate_pct,
                               base_rate_cd, margin_pct
                        FROM public.bank_scheme_rate
                        WHERE schm_id = ?
                          AND rate_sts = 'ACTIVE'
                          AND eff_dt <= ?
                        ORDER BY eff_dt DESC, schm_rate_id DESC
                        LIMIT 1
                        """,
                request.schmId(), request.placementDate()
        );
        if (rates.isEmpty()) throw conflict("No applicable scheme rate for placement date");
        Map<String, Object> configured = rates.get(0);
        String rateType = (String) configured.get("rate_typ");
        String baseCode = null;
        BigDecimal margin = null;
        BigDecimal interestRate;
        if ("FIXED".equals(rateType)) {
            BigDecimal standard = (BigDecimal) configured.get("fixed_rate_pct");
            if (standard == null) throw conflict("Fixed scheme has no configured rate");
            BigDecimal minimum = (BigDecimal) configured.get("min_rate_pct");
            BigDecimal maximum = (BigDecimal) configured.get("max_rate_pct");
            interestRate = request.interestRate() == null ? standard : request.interestRate();
            if (interestRate.signum() < 0
                    || interestRate.compareTo(minimum == null ? standard : minimum) < 0
                    || interestRate.compareTo(maximum == null ? standard : maximum) > 0) {
                throw conflict("Selected interest rate is outside the effective scheme rate range");
            }
        } else if ("FLOATING".equals(rateType)) {
            // A scheme reference rate may be saved on an Islamic DRAFT as an indicative
            // figure only. It is NOT a contractual Islamic profit calculation, and
            // Islamic placement, accrual, payout and settlement remain blocked by
            // BankIslamicDepositGuard in their respective financial services.
            baseCode = (String) configured.get("base_rate_cd");
            margin = (BigDecimal) configured.get("margin_pct");
            interestRate = floatingRates.rateOn(
                    baseCode, margin, request.placementDate());
            if (request.interestRate() != null && request.interestRate().compareTo(interestRate) != 0) {
                throw conflict("Selected floating rate no longer matches the applicable base rate and margin");
            }
        } else {
            throw conflict("Unsupported scheme rate type");
        }

        // Approved configuration is necessary for creating an
        // Islamic DRAFT. Neither the saved indicative rate nor the profile
        // authorizes interest/profit accounting, funding or activation.
        boolean islamic = "ISLAMIC".equals(product.get("banking_typ"));
        if (!islamic && !"CONVENTIONAL".equals(product.get("banking_typ"))) {
            throw conflict("Unsupported deposit banking classification");
        }
        Map<String, Object> islamicProfile = null;
        if (islamic) {
            // Contract approval authorizes draft preparation only; the rate type
            // must not accidentally bypass this mandatory review.
            islamicProfile = islamicDrafts.lockApprovedProfile(request.schmId(), request.prodId());
        }

        LocalDate maturityDate = maturityService.calculate(
                request.placementDate(),
                request.tenorMonths(),
                "NONE"
        ).contractualMaturityDate();

        String reference = BusinessReferenceGenerator.next(jdbc, BusinessReferenceGenerator.Stream.FIXED_DEPOSIT);

        Long fdId;
        try {
            fdId = jdbc.queryForObject(
                """
                        INSERT INTO public.bank_fixed_deposit
                        (
                            fd_ref,
                            cust_id,
                            prod_id,
                            schm_id,
                            fd_typ,
                            principal_amt,
                            tenor_mth,
                            placement_dt,
                            maturity_dt,
                            interest_rate,
                            rate_typ,
                            base_rate_cd,
                            margin_pct,
                            fd_sts,
                            remarks,
                            crt_by
                        )
                        VALUES (
                            ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                            ?, ?, ?, 'DRAFT', ?, ?
                        )
                        RETURNING fd_id
                        """,
                Long.class,
                reference,
                request.custId(),
                request.prodId(),
                request.schmId(),
                request.fdType(),
                request.principalAmount(),
                request.tenorMonths(),
                request.placementDate(),
                maturityDate,
                interestRate,
                rateType,
                baseCode,
                margin,
                request.remarks(),
                authentication.getName()
            );
        } catch (DataIntegrityViolationException exception) {
            throw draftDatabaseConflict("deposit row", exception);
        }

        if (islamic) {
            // The approved terms are copied in this same transaction. Failure
            // rolls back the FD row; no orphan Islamic draft is possible.
            try {
                islamicDrafts.captureDraft(fdId, request.schmId(), request.prodId(),
                        islamicProfile, interestRate, authentication.getName());
            } catch (DataIntegrityViolationException exception) {
                throw draftDatabaseConflict("Islamic contract snapshot", exception);
            }
        }
        return getInternal(fdId);
    }


    /**
     * Temporary development diagnostic: return only a SQLSTATE and bounded
     * constraint identifier, never PostgreSQL DETAIL, SQL parameters, or customer data.
     * Remove the diagnostic detail before deploying to production.
     */
    private ResponseStatusException draftDatabaseConflict(
            String stage, DataIntegrityViolationException exception) {
        SQLException sqlError = null;
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException found) sqlError = found;
        }
        String state = sqlError != null && sqlError.getSQLState() != null
                && sqlError.getSQLState().matches("[A-Za-z0-9]{5}")
                ? sqlError.getSQLState() : "unknown";
        String constraint = "unknown";
        if (sqlError != null && sqlError.getMessage() != null) {
            Matcher match = Pattern.compile("(?i)\\bconstraint \"([A-Za-z_][A-Za-z0-9_]{0,79})\"")
                    .matcher(sqlError.getMessage());
            if (match.find()) constraint = match.group(1);
        }
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Deposit draft database conflict at " + stage
                        + " (SQLSTATE " + state + ", constraint " + constraint + ")", exception);
    }

    @Transactional(readOnly = true)
    public BankFixedDepositResponse get(Long fdId) {
        return getInternal(fdId);
    }

    @Transactional(readOnly = true)
    public List<BankFixedDepositResponse> list() {
        return jdbc.query(
                """
                        SELECT *
                        FROM public.bank_fixed_deposit
                        ORDER BY fd_id DESC
                        """,
                (rs, rowNum) -> mapResponse(rs)
        );
    }

    private BankFixedDepositResponse getInternal(Long fdId) {
        List<BankFixedDepositResponse> rows = jdbc.query(
                """
                        SELECT *
                        FROM public.bank_fixed_deposit
                        WHERE fd_id = ?
                        """,
                (rs, rowNum) -> mapResponse(rs),
                fdId
        );

        if (rows.isEmpty()) {
            throw notFound("Fixed deposit not found");
        }

        return rows.get(0);
    }

    private BankFixedDepositResponse mapResponse(
            ResultSet rs
    ) throws SQLException {
        return new BankFixedDepositResponse(
                rs.getLong("fd_id"),
                rs.getString("fd_ref"),
                rs.getLong("cust_id"),
                rs.getLong("prod_id"),
                rs.getLong("schm_id"),
                rs.getString("fd_typ"),
                rs.getString("ccy_cd"),
                rs.getBigDecimal("principal_amt"),
                rs.getInt("tenor_mth"),
                rs.getDate("placement_dt").toLocalDate(),
                rs.getDate("maturity_dt").toLocalDate(),
                rs.getString("rate_typ"),
                rs.getBigDecimal("interest_rate"),
                rs.getString("fd_sts"),
                rs.getString("remarks")
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                message
        );
    }
}
