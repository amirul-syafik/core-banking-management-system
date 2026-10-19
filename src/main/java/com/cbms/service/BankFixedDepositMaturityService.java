package com.cbms.service;

import com.cbms.dto.BankFixedDepositMaturityRequest;
import com.cbms.dto.BankFixedDepositMaturityResponse;
import com.cbms.dto.BankFixedDepositMaturityValidationResponse;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class BankFixedDepositMaturityService {

    private final JdbcTemplate jdbc;

    public BankFixedDepositMaturityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BankFixedDepositMaturityResponse calculate(
            BankFixedDepositMaturityRequest request
    ) {
        return calculate(
                request.placementDate(),
                request.tenorMonths(),
                request.weekendConvention()
        );
    }

    public BankFixedDepositMaturityResponse calculate(
            LocalDate placementDate,
            Integer tenorMonths,
            String convention
    ) {
        if (placementDate == null) {
            throw new IllegalArgumentException(
                    "Placement date is required"
            );
        }

        if (tenorMonths == null
                || tenorMonths < 1
                || tenorMonths > 600) {
            throw new IllegalArgumentException(
                    "Tenor must be between 1 and 600 months"
            );
        }

        if (!List.of("NONE", "FOLLOWING", "PRECEDING")
                .contains(convention)) {
            throw new IllegalArgumentException(
                    "Invalid weekend convention"
            );
        }

        boolean isMonthEnd =
                placementDate.getDayOfMonth()
                        == placementDate.lengthOfMonth();

        LocalDate maturityDate =
                placementDate.plusMonths(tenorMonths);

        // Explicit month-end convention.
        // E.g. 30 Apr + 1 month = 31 May.
        if (isMonthEnd) {
            maturityDate = YearMonth.from(maturityDate)
                    .atEndOfMonth();
        }

        LocalDate paymentDate =
                adjustWeekend(maturityDate, convention);

        return new BankFixedDepositMaturityResponse(
                placementDate,
                tenorMonths,
                maturityDate,
                paymentDate,
                convention,
                isMonthEnd,
                !paymentDate.equals(maturityDate)
        );
    }

    @Transactional(readOnly = true)
    public BankFixedDepositMaturityResponse preview(
            Long fdId,
            String weekendConvention
    ) {
        DepositRecord fd = loadDeposit(fdId);

        return calculate(
                fd.placementDate(),
                fd.tenorMonths(),
                weekendConvention
        );
    }

    @Transactional(readOnly = true)
    public BankFixedDepositMaturityValidationResponse validate(
            Long fdId
    ) {
        DepositRecord fd = loadDeposit(fdId);

        LocalDate calculated = calculate(
                fd.placementDate(),
                fd.tenorMonths(),
                "NONE"
        ).contractualMaturityDate();

        boolean valid = calculated.equals(fd.maturityDate());

        return new BankFixedDepositMaturityValidationResponse(
                fd.fdId(),
                fd.fdReference(),
                fd.status(),
                fd.placementDate(),
                fd.tenorMonths(),
                fd.maturityDate(),
                calculated,
                valid,
                valid
                        ? "Stored maturity date is correct"
                        : "Stored maturity date differs from calculated maturity"
        );
    }

    private LocalDate adjustWeekend(
            LocalDate date,
            String convention
    ) {
        if ("NONE".equals(convention)) {
            return date;
        }

        LocalDate result = date;

        while (isWeekend(result)) {
            result = "FOLLOWING".equals(convention)
                    ? result.plusDays(1)
                    : result.minusDays(1);
        }

        return result;
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();

        return day == DayOfWeek.SATURDAY
                || day == DayOfWeek.SUNDAY;
    }

    private DepositRecord loadDeposit(Long fdId) {
        List<DepositRecord> rows = jdbc.query(
                """
                SELECT
                    fd_id,
                    fd_ref,
                    fd_sts,
                    placement_dt,
                    tenor_mth,
                    maturity_dt
                FROM public.bank_fixed_deposit
                WHERE fd_id = ?
                """,
                this::mapDeposit,
                fdId
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Fixed deposit not found"
            );
        }

        return rows.get(0);
    }

    private DepositRecord mapDeposit(
            ResultSet rs,
            int rowNum
    ) throws SQLException {
        return new DepositRecord(
                rs.getLong("fd_id"),
                rs.getString("fd_ref"),
                rs.getString("fd_sts"),
                rs.getDate("placement_dt").toLocalDate(),
                rs.getInt("tenor_mth"),
                rs.getDate("maturity_dt").toLocalDate()
        );
    }

    private record DepositRecord(
            Long fdId,
            String fdReference,
            String status,
            LocalDate placementDate,
            Integer tenorMonths,
            LocalDate maturityDate
    ) {}
}
