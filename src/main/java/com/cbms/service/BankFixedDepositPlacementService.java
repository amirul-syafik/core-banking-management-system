package com.cbms.service;

import com.cbms.dto.BankFixedDepositPlacementRequest;
import com.cbms.dto.BankFixedDepositPlacementResponse;

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

@Service
public class BankFixedDepositPlacementService {

    private final JdbcTemplate jdbc;
    private final BankFixedDepositFundingPort fundingPort;
    private final BankFixedDepositMaturityService maturityService;

    public BankFixedDepositPlacementService(
            JdbcTemplate jdbc,
            BankFixedDepositFundingPort fundingPort,
            BankFixedDepositMaturityService maturityService
    ) {
        this.jdbc = jdbc;
        this.fundingPort = fundingPort;
        this.maturityService = maturityService;
    }

    @Transactional
    public BankFixedDepositPlacementResponse place(
            Long fdId,
            BankFixedDepositPlacementRequest request,
            Authentication authentication
    ) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                SELECT *
                FROM public.bank_fixed_deposit
                WHERE fd_id = ?
                FOR UPDATE
                """,
                fdId
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Fixed deposit not found"
            );
        }

        Map<String, Object> fd = rows.get(0);

        if (!"DRAFT".equals(fd.get("fd_sts"))) {
            throw conflict(
                    "Only DRAFT fixed deposits can be placed"
            );
        }

        BigDecimal principal =
                (BigDecimal) fd.get("principal_amt");

        Long customerId =
                ((Number) fd.get("cust_id")).longValue();

        Integer tenorMonths =
                ((Number) fd.get("tenor_mth")).intValue();

        if (principal.signum() <= 0) {
            throw conflict("Invalid fixed deposit principal");
        }

        if (!"MYR".equals(fd.get("ccy_cd"))) {
            throw conflict("Only MYR placements are supported");
        }

        // This is a snapshot of the database business date.
        LocalDate placementDate = jdbc.queryForObject(
                "SELECT CURRENT_DATE",
                LocalDate.class
        );

        LocalDate maturityDate = maturityService.calculate(
                placementDate,
                tenorMonths,
                "NONE"
        ).contractualMaturityDate();

        // The adapter must participate in this transaction.
        // If any subsequent operation fails, the debit and
        // ledger postings must roll back as well.
        String postingReference =
                fundingPort.debitForPlacement(
                        request.sourceAccountId(),
                        customerId,
                        principal,
                        request.placementReference(),
                        authentication.getName()
                );

        if (postingReference == null
                || postingReference.isBlank()) {
            throw conflict(
                    "Funding did not return a posting reference"
            );
        }

        Long placementId = jdbc.queryForObject(
                """
                INSERT INTO public.bank_fixed_deposit_placement
                (
                    placement_ref,
                    fd_id,
                    source_acct_id,
                    placement_amt,
                    placement_dt,
                    posting_ref,
                    remarks,
                    crt_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING placement_id
                """,
                Long.class,
                request.placementReference(),
                fdId,
                request.sourceAccountId(),
                principal,
                placementDate,
                postingReference,
                request.remarks(),
                authentication.getName()
        );

        int updated = jdbc.update(
                """
                UPDATE public.bank_fixed_deposit
                SET fd_sts = 'ACTIVE',
                    placement_dt = ?,
                    maturity_dt = ?,
                    upd_dt = CURRENT_TIMESTAMP,
                    upd_by = ?
                WHERE fd_id = ?
                  AND fd_sts = 'DRAFT'
                """,
                placementDate,
                maturityDate,
                authentication.getName(),
                fdId
        );

        if (updated != 1) {
            throw conflict("Fixed deposit activation failed");
        }

        return getInternal(placementId);
    }

    @Transactional(readOnly = true)
    public BankFixedDepositPlacementResponse getByDeposit(
            Long fdId
    ) {
        List<Long> ids = jdbc.queryForList(
                """
                SELECT placement_id
                FROM public.bank_fixed_deposit_placement
                WHERE fd_id = ?
                """,
                Long.class,
                fdId
        );

        if (ids.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Placement not found"
            );
        }

        return getInternal(ids.get(0));
    }

    private BankFixedDepositPlacementResponse getInternal(
            Long placementId
    ) {
        List<BankFixedDepositPlacementResponse> rows =
                jdbc.query(
                        """
                        SELECT
                            p.placement_id,
                            p.placement_ref,
                            p.fd_id,
                            p.source_acct_id,
                            p.placement_amt,
                            p.ccy_cd,
                            p.placement_dt,
                            p.posting_ref,
                            p.placement_sts,
                            f.maturity_dt,
                            f.fd_sts
                        FROM public.bank_fixed_deposit_placement p
                        JOIN public.bank_fixed_deposit f
                          ON f.fd_id = p.fd_id
                        WHERE p.placement_id = ?
                        """,
                        (rs, rowNum) ->
                                new BankFixedDepositPlacementResponse(
                                        rs.getLong("placement_id"),
                                        rs.getString("placement_ref"),
                                        rs.getLong("fd_id"),
                                        rs.getLong("source_acct_id"),
                                        rs.getBigDecimal("placement_amt"),
                                        rs.getString("ccy_cd"),
                                        rs.getDate("placement_dt")
                                                .toLocalDate(),
                                        rs.getDate("maturity_dt")
                                                .toLocalDate(),
                                        rs.getString("posting_ref"),
                                        rs.getString("placement_sts"),
                                        rs.getString("fd_sts")
                                ),
                        placementId
                );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Placement not found"
            );
        }

        return rows.get(0);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}
