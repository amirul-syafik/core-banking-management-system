package com.cbms.service;

import com.cbms.dto.OperationalReportPage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OperationalReportingService {
    private final JdbcTemplate jdbc;

    public OperationalReportingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public OperationalReportPage report(String type, Long custId, String status,
                                        String currency, LocalDate fromDate,
                                        LocalDate toDate, int page, int size) {
        RequestFilterValidation.pagination(page, size);
        if (custId != null && custId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "custId must be positive");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate must not exceed toDate");
        }
        currency = RequestFilterValidation.optionalUpper(currency);
        status = RequestFilterValidation.optionalUpper(status);
        if (currency != null && !currency.matches("[A-Z]{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currency must be a 3-letter code");
        }
        if (status != null && !status.matches("[A-Z_]{1,30}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status");
        }
        String table, fields, customerCol, statusCol, currencyCol, dateCol, idCol;
        switch (type) {
            case "accounts" -> {
                table = "public.bank_account";
                fields = "acct_id, cust_id, acct_no, acct_typ, ccy_cd, acct_sts, open_dt, close_dt";
                customerCol = "cust_id";
                statusCol = "acct_sts";
                currencyCol = "ccy_cd";
                dateCol = "open_dt";
                idCol = "acct_id";
            }
            case "financing" -> {
                table = "public.bank_financing_facility";
                fields = "fac_id, fac_ref, cust_id, fac_typ, ccy_cd, appr_amt, fac_sts, appr_dt, book_dt, maturity_dt";
                customerCol = "cust_id";
                statusCol = "fac_sts";
                currencyCol = "ccy_cd";
                dateCol = "book_dt";
                idCol = "fac_id";
            }
            case "fixed-deposits" -> {
                table = "public.bank_fixed_deposit";
                fields = "fd_id, fd_ref, cust_id, ccy_cd, principal_amt, interest_rate, fd_sts, placement_dt, maturity_dt";
                customerCol = "cust_id";
                statusCol = "fd_sts";
                currencyCol = "ccy_cd";
                dateCol = "placement_dt";
                idCol = "fd_id";
            }
            case "collaterals" -> {
                table = "public.bank_collateral";
                fields = "collateral_id, collateral_ref, owner_cust_id, collateral_typ, collateral_desc, collateral_sts, vehicle_id, crt_dt";
                customerCol = "owner_cust_id";
                statusCol = "collateral_sts";
                currencyCol = null;
                dateCol = "crt_dt";
                idCol = "collateral_id";
            }
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown report: " + type);
        }
        if (currency != null && currencyCol == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency filter is not applicable to collateral");
        }
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (custId != null) {
            where.append(" AND ").append(customerCol).append(" = ?");
            args.add(custId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND ").append(statusCol).append(" = ?");
            args.add(status);
        }
        if (currency != null) {
            where.append(" AND ").append(currencyCol).append(" = ?");
            args.add(currency);
        }
        if (fromDate != null) {
            where.append(" AND ").append(dateCol).append(" >= ?");
            args.add(fromDate);
        }
        if (toDate != null) {
            // Exclusive upper bound supports timestamp and date columns consistently.
            where.append(" AND ").append(dateCol).append(" < ?");
            args.add(toDate.plusDays(1));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + where, Long.class, args.toArray());
        long count = total == null ? 0L : total;
        long offset = (long) page * size;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + fields + " FROM " + table + where +
                        " ORDER BY " + idCol + " DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return new OperationalReportPage(type, page, size, count,
                (count + size - 1) / size, rows);
    }
}
