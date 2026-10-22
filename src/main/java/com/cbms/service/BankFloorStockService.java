package com.cbms.service;

import com.cbms.dto.*;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BankFloorStockService {

    private final JdbcTemplate jdbc;

    public BankFloorStockService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public BankFloorStockResponse finance(
            Long facId,
            BankFloorStockCreateRequest request,
            Authentication authentication
    ) {
        Map<String, Object> facility = lockFacility(facId);

        validateFacility(facility);

        BigDecimal approved =
                (BigDecimal) facility.get("appr_limit");

        BigDecimal utilized =
                (BigDecimal) facility.get("utilized_amt");

        BigDecimal available = approved.subtract(utilized);

        BigDecimal financeAmount = request.financedAmount();

        if (financeAmount.compareTo(request.purchasePrice()) > 0) {
            throw conflict(
                    "Financing amount cannot exceed purchase price"
            );
        }

        if (financeAmount.compareTo(available) > 0) {
            throw conflict("Insufficient available credit limit");
        }

        String vin = request.vinNo().trim().toUpperCase();

        Integer duplicate = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.bank_floor_stock
                        WHERE cl_fac_id = ?
                          AND vin_no = ?
                        """,
                Integer.class,
                facId,
                vin
        );

        if (duplicate != null && duplicate > 0) {
            throw conflict("Vehicle VIN already registered in facility");
        }

        String stockReference =
                "FS-" + UUID.randomUUID();

        Long stockId;

        try {
            stockId = jdbc.queryForObject(
                    """
                            INSERT INTO public.bank_floor_stock
                            (
                                stock_ref,
                                cl_fac_id,
                                vin_no,
                                vehicle_make,
                                vehicle_model,
                                manufacture_year,
                                purchase_price,
                                financed_amt,
                                outstanding_amt,
                                stock_sts,
                                remarks,
                                crt_by
                            )
                            VALUES (
                                ?, ?, ?, ?, ?, ?, ?, ?, ?,
                                'FINANCED', ?, ?
                            )
                            RETURNING stock_id
                            """,
                    Long.class,
                    stockReference,
                    facId,
                    vin,
                    request.vehicleMake().trim(),
                    request.vehicleModel().trim(),
                    request.manufactureYear(),
                    request.purchasePrice(),
                    financeAmount,
                    financeAmount,
                    request.remarks(),
                    authentication.getName()
            );
        } catch (DuplicateKeyException ex) {
            throw conflict("Duplicate floor stock reference or VIN");
        }

        int updated = jdbc.update(
                """
                        UPDATE public.bank_credit_line_facility
                        SET utilized_amt = utilized_amt + ?,
                            upd_dt = CURRENT_TIMESTAMP,
                            upd_by = ?
                        WHERE cl_fac_id = ?
                          AND cl_fac_sts = 'ACTIVE'
                          AND utilized_amt + ? <= appr_limit
                        """,
                financeAmount,
                authentication.getName(),
                facId,
                financeAmount
        );

        if (updated != 1) {
            throw conflict("Unable to update credit facility utilization");
        }

        return getInternal(stockId);
    }

    @Transactional
    public BankFloorStockResponse settle(
            Long stockId,
            BankFloorStockSettlementRequest request,
            Authentication authentication
    ) {
        // Read the facility ID first. Lock ordering is always:
        // facility -> stock, consistent with financing operations.
        Long facId = jdbc.query(
                """
                        SELECT cl_fac_id
                        FROM public.bank_floor_stock
                        WHERE stock_id = ?
                        """,
                rs -> rs.next() ? rs.getLong(1) : null,
                stockId
        );

        if (facId == null) {
            throw notFound("Floor stock not found");
        }

        Map<String, Object> facility = lockFacility(facId);

        if (!"ACTIVE".equals(facility.get("cl_fac_sts"))) {
            throw conflict("Facility must be ACTIVE");
        }

        if (!"FLOOR_STOCKING".equals(facility.get("cl_fac_typ"))) {
            throw conflict("Facility is not FLOOR_STOCKING");
        }

        List<Map<String, Object>> stocks = jdbc.queryForList(
                """
                        SELECT *
                        FROM public.bank_floor_stock
                        WHERE stock_id = ?
                          AND cl_fac_id = ?
                        FOR UPDATE
                        """,
                stockId,
                facId
        );

        if (stocks.isEmpty()) {
            throw notFound("Floor stock not found");
        }

        Map<String, Object> stock = stocks.get(0);

        if (!"FINANCED".equals(stock.get("stock_sts"))) {
            throw conflict("Floor stock is already settled");
        }

        BigDecimal outstanding =
                (BigDecimal) stock.get("outstanding_amt");

        BigDecimal utilized =
                (BigDecimal) facility.get("utilized_amt");

        if (outstanding.signum() <= 0) {
            throw conflict("No outstanding amount to settle");
        }

        if (outstanding.compareTo(utilized) > 0) {
            throw conflict(
                    "Stock outstanding exceeds facility utilization"
            );
        }

        String settlementReference =
                request.settlementReference().trim();

        if (settlementReference.isEmpty()) {
            throw conflict("Settlement reference is required");
        }

        Integer duplicate = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.bank_floor_stock_settlement
                        WHERE settlement_ref = ?
                        """,
                Integer.class,
                settlementReference
        );

        if (duplicate != null && duplicate > 0) {
            throw conflict("Settlement reference already exists");
        }

        try {
            jdbc.update(
                    """
                            INSERT INTO public.bank_floor_stock_settlement
                            (
                                settlement_ref,
                                stock_id,
                                settlement_amt,
                                reason,
                                settlement_by
                            )
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    settlementReference,
                    stockId,
                    outstanding,
                    request.reason().trim(),
                    authentication.getName()
            );
        } catch (DuplicateKeyException ex) {
            throw conflict("Duplicate settlement reference or stock");
        }

        int updatedStock = jdbc.update(
                """
                        UPDATE public.bank_floor_stock
                        SET outstanding_amt = 0,
                            stock_sts = 'SETTLED',
                            settlement_dt = CURRENT_DATE,
                            upd_dt = CURRENT_TIMESTAMP,
                            upd_by = ?
                        WHERE stock_id = ?
                          AND stock_sts = 'FINANCED'
                        """,
                authentication.getName(),
                stockId
        );

        if (updatedStock != 1) {
            throw conflict("Unable to settle floor stock");
        }

        int updatedFacility = jdbc.update(
                """
                        UPDATE public.bank_credit_line_facility
                        SET utilized_amt = utilized_amt - ?,
                            upd_dt = CURRENT_TIMESTAMP,
                            upd_by = ?
                        WHERE cl_fac_id = ?
                          AND utilized_amt >= ?
                        """,
                outstanding,
                authentication.getName(),
                facId,
                outstanding
        );

        if (updatedFacility != 1) {
            throw conflict("Unable to restore credit facility limit");
        }

        return getInternal(stockId);
    }

    @Transactional(readOnly = true)
    public BankFloorStockResponse get(Long stockId) {
        return getInternal(stockId);
    }

    @Transactional(readOnly = true)
    public List<BankFloorStockResponse> list(Long facId) {
        Integer count = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.bank_credit_line_facility
                        WHERE cl_fac_id = ?
                        """,
                Integer.class,
                facId
        );

        if (count == null || count == 0) {
            throw notFound("Credit line facility not found");
        }

        return jdbc.query(
                """
                        SELECT *
                        FROM public.bank_floor_stock
                        WHERE cl_fac_id = ?
                        ORDER BY stock_id DESC
                        """,
                (rs, rowNum) -> mapResponse(rs),
                facId
        );
    }

    private BankFloorStockResponse getInternal(Long stockId) {
        List<BankFloorStockResponse> results = jdbc.query(
                """
                        SELECT *
                        FROM public.bank_floor_stock
                        WHERE stock_id = ?
                        """,
                (rs, rowNum) -> mapResponse(rs),
                stockId
        );

        if (results.isEmpty()) {
            throw notFound("Floor stock not found");
        }

        return results.get(0);
    }

    private BankFloorStockResponse mapResponse(
            java.sql.ResultSet rs
    ) throws java.sql.SQLException {
        Date settlementDate = rs.getDate("settlement_dt");

        return new BankFloorStockResponse(
                rs.getLong("stock_id"),
                rs.getString("stock_ref"),
                rs.getLong("cl_fac_id"),
                rs.getString("vin_no"),
                rs.getString("vehicle_make"),
                rs.getString("vehicle_model"),
                rs.getInt("manufacture_year"),
                rs.getBigDecimal("purchase_price"),
                rs.getBigDecimal("financed_amt"),
                rs.getBigDecimal("outstanding_amt"),
                rs.getString("stock_sts"),
                rs.getDate("finance_dt").toLocalDate(),
                settlementDate == null
                        ? null
                        : settlementDate.toLocalDate(),
                rs.getString("remarks")
        );
    }

    private Map<String, Object> lockFacility(Long facId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        SELECT *
                        FROM public.bank_credit_line_facility
                        WHERE cl_fac_id = ?
                        FOR UPDATE
                        """,
                facId
        );

        if (rows.isEmpty()) {
            throw notFound("Credit line facility not found");
        }

        return rows.get(0);
    }

    private void validateFacility(Map<String, Object> facility) {
        if (!"ACTIVE".equals(facility.get("cl_fac_sts"))) {
            throw conflict("Facility must be ACTIVE");
        }

        if (!"FLOOR_STOCKING".equals(facility.get("cl_fac_typ"))) {
            throw conflict("Facility must be FLOOR_STOCKING");
        }

        if (!"MYR".equals(facility.get("ccy_cd"))) {
            throw conflict("Only MYR floor stocking is supported");
        }

        LocalDate today = LocalDate.now();

        LocalDate effective =
                ((Date) facility.get("eff_dt")).toLocalDate();

        LocalDate expiry =
                ((Date) facility.get("expiry_dt")).toLocalDate();

        if (today.isBefore(effective) || !today.isBefore(expiry)) {
            throw conflict("Facility is outside its effective period");
        }
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
