package com.cbms.service;

import com.cbms.dto.OperationalReportPage;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportingDashboardExportService {
    private static final int EXPORT_LIMIT = 10000;
    private final JdbcTemplate jdbc;
    private final OperationalReportingService reports;

    public ReportingDashboardExportService(JdbcTemplate jdbc, OperationalReportingService reports) {
        this.jdbc = jdbc;
        this.reports = reports;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customers", count("public.customer_master"));
        result.put("accounts", count("public.bank_account"));
        result.put("financingFacilities", count("public.bank_financing_facility"));
        result.put("fixedDeposits", count("public.bank_fixed_deposit"));
        result.put("collaterals", count("public.bank_collateral"));
        result.put("accountBalancesByCurrency", jdbc.queryForList(
                "SELECT a.ccy_cd AS currency, COALESCE(SUM(b.ledger_bal), 0) AS ledgerBalance, " +
                        "COALESCE(SUM(b.avail_bal), 0) AS availableBalance " +
                        "FROM public.bank_account a JOIN public.bank_account_balance b ON b.acct_id = a.acct_id " +
                        "GROUP BY a.ccy_cd ORDER BY a.ccy_cd"));
        result.put("financingApprovedByCurrency", jdbc.queryForList(
                "SELECT ccy_cd AS currency, COALESCE(SUM(appr_amt), 0) AS approvedAmount " +
                        "FROM public.bank_financing_facility GROUP BY ccy_cd ORDER BY ccy_cd"));
        result.put("fixedDepositPrincipalByCurrency", jdbc.queryForList(
                "SELECT ccy_cd AS currency, COALESCE(SUM(principal_amt), 0) AS principalAmount " +
                        "FROM public.bank_fixed_deposit WHERE fd_sts = 'ACTIVE' " +
                        "GROUP BY ccy_cd ORDER BY ccy_cd"));
        return result;
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0 : value;
    }

    @Transactional(readOnly = true)
    public byte[] export(String type, Long custId, String status, String currency,
                         LocalDate fromDate, LocalDate toDate) {
        OperationalReportPage first = reports.report(type, custId, status, currency, fromDate, toDate, 0, 100);
        if (first.totalElements() > EXPORT_LIMIT) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Export exceeds 10000 records; narrow the report filters");
        }
        String[] headers = switch (type) {
            case "accounts" ->
                    new String[]{"acct_id", "cust_id", "acct_no", "acct_typ", "ccy_cd", "acct_sts", "open_dt", "close_dt"};
            case "financing" ->
                    new String[]{"fac_id", "fac_ref", "cust_id", "fac_typ", "ccy_cd", "appr_amt", "fac_sts", "appr_dt", "book_dt", "maturity_dt"};
            case "fixed-deposits" ->
                    new String[]{"fd_id", "fd_ref", "cust_id", "ccy_cd", "principal_amt", "interest_rate", "fd_sts", "placement_dt", "maturity_dt"};
            case "collaterals" ->
                    new String[]{"collateral_id", "collateral_ref", "owner_cust_id", "collateral_typ", "collateral_desc", "collateral_sts", "vehicle_id", "crt_dt"};
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown report: " + type);
        };
        StringBuilder csv = new StringBuilder(String.join(",", headers)).append("\r\n");
        appendRows(csv, headers, first.records());
        for (int page = 1; page < first.totalPages(); page++) {
            OperationalReportPage next = reports.report(type, custId, status, currency, fromDate, toDate, page, 100);
            appendRows(csv, headers, next.records());
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendRows(StringBuilder csv, String[] headers, List<Map<String, Object>> records) {
        for (Map<String, Object> record : records) {
            for (int i = 0; i < headers.length; i++) {
                if (i > 0) csv.append(',');
                Object raw = record.get(headers[i]);
                String value = raw == null ? "" : raw.toString();
                // Prevent spreadsheet formula execution when CSV is opened in Excel/Sheets.
                if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) value = "'" + value;
                csv.append('"').append(value.replace("\"", "\"\"")).append('"');
            }
            csv.append("\r\n");
        }
    }
}
