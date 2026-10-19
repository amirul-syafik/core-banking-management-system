package com.cbms.service;

import com.cbms.dto.BankFixedDepositCreateRequest;
import com.cbms.dto.BankFixedDepositResponse;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BankFixedDepositService {

    private final JdbcTemplate jdbc;
    private final BankFixedDepositMaturityService maturityService;

    public BankFixedDepositService(
            JdbcTemplate jdbc,
            BankFixedDepositMaturityService maturityService
    ) {
        this.jdbc = jdbc;
        this.maturityService = maturityService;
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

        // Resolve the latest effective fixed interest rate
        List<BigDecimal> rates = jdbc.queryForList(
                """
                SELECT fixed_rate_pct
                FROM public.bank_scheme_rate
                WHERE schm_id = ?
                  AND rate_typ = 'FIXED'
                  AND rate_sts = 'ACTIVE'
                  AND eff_dt <= ?
                ORDER BY eff_dt DESC
                LIMIT 1
                """,
                BigDecimal.class,
                request.schmId(),
                request.placementDate()
        );

        if (rates.isEmpty() || rates.get(0) == null) {
            throw conflict(
                    "No active fixed interest rate found"
            );
        }

        BigDecimal interestRate = rates.get(0);

        LocalDate maturityDate = maturityService.calculate(
                request.placementDate(),
                request.tenorMonths(),
                "NONE"
        ).contractualMaturityDate();

        String reference =
                "FD-" + UUID.randomUUID();

        Long fdId = jdbc.queryForObject(
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
                    fd_sts,
                    remarks,
                    crt_by
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    'DRAFT', ?, ?
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
                request.remarks(),
                authentication.getName()
        );

        return getInternal(fdId);
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
