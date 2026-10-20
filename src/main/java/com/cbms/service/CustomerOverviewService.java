package com.cbms.service;

import com.cbms.dto.CustomerOverviewResponse;
import com.cbms.entity.*;
import com.cbms.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CustomerOverviewService {
    private final CustomerRepository customers;
    private final BankAccountRepository accounts;
    private final BankAccountBalanceRepository balances;
    private final BankFinancingFacilityRepository facilities;
    private final BankCollateralRepository collaterals;
    private final BankFinancingCollateralRepository links;
    private final JdbcTemplate jdbc;

    public CustomerOverviewService(CustomerRepository customers,
                                   BankAccountRepository accounts,
                                   BankAccountBalanceRepository balances,
                                   BankFinancingFacilityRepository facilities,
                                   BankCollateralRepository collaterals,
                                   BankFinancingCollateralRepository links,
                                   JdbcTemplate jdbc) {
        this.customers = customers;
        this.accounts = accounts;
        this.balances = balances;
        this.facilities = facilities;
        this.collaterals = collaterals;
        this.links = links;
        this.jdbc = jdbc;
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String currency(String value) {
        return value == null ? "UNKNOWN" : value;
    }

    private static void add(Map<String, BigDecimal> totals, String ccy, BigDecimal value) {
        totals.merge(currency(ccy), amount(value), BigDecimal::add);
    }

    private static List<CustomerOverviewResponse.CurrencyAmount> currencyAmounts(Map<String, BigDecimal> totals) {
        return totals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new CustomerOverviewResponse.CurrencyAmount(e.getKey(), e.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerOverviewResponse getOverview(Long custId) {
        Customer customer = customers.findById(custId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        List<BankAccount> customerAccounts = accounts.findByCustIdOrderByAcctIdDesc(custId);
        long activeAccounts = customerAccounts.stream().filter(a -> "ACTIVE".equalsIgnoreCase(a.getAcctSts())).count();
        Map<String, BigDecimal> ledger = new HashMap<>();
        Map<String, BigDecimal> available = new HashMap<>();
        Map<Long, BankAccountBalance> balancesByAccount = customerAccounts.isEmpty()
                ? Map.of()
                : balances.findByAcctIdIn(customerAccounts.stream()
                        .map(BankAccount::getAcctId).toList()).stream()
                .collect(Collectors.toMap(BankAccountBalance::getAcctId, b -> b));
        for (BankAccount a : customerAccounts) {
            BankAccountBalance b = balancesByAccount.get(a.getAcctId());
            if (b != null) {
                add(ledger, a.getCcyCd(), b.getLedgerBal());
                add(available, a.getCcyCd(), b.getAvailBal());
            }
        }

        List<BankFinancingFacility> customerFacilities = facilities.findByCustIdOrderByFacIdDesc(custId);
        Map<String, BigDecimal> approved = new HashMap<>();
        for (BankFinancingFacility f : customerFacilities) {
            add(approved, f.getCcyCd(), f.getApprAmt());
        }
        long activeFacilities = customerFacilities.stream().filter(f ->
                Set.of("ACTIVE", "BOOKED", "DISBURSED").contains(
                        Objects.toString(f.getFacSts(), "").toUpperCase(Locale.ROOT))).count();

        // Fixed deposits are implemented using JDBC in the existing CBMS source.
        // Report active principal separately per currency; do not mix MYR with other currencies.
        List<Map<String, Object>> fdRows = jdbc.queryForList("""
                SELECT ccy_cd, COUNT(*) AS total_count,
                       COUNT(*) FILTER (WHERE fd_sts = 'ACTIVE') AS active_count,
                       COALESCE(SUM(principal_amt) FILTER (WHERE fd_sts = 'ACTIVE'), 0) AS active_principal
                FROM public.bank_fixed_deposit
                WHERE cust_id = ?
                GROUP BY ccy_cd
                """, custId);
        long fdTotal = 0;
        long fdActive = 0;
        Map<String, BigDecimal> fdPrincipal = new HashMap<>();
        for (Map<String, Object> row : fdRows) {
            fdTotal += ((Number) row.get("total_count")).longValue();
            fdActive += ((Number) row.get("active_count")).longValue();
            add(fdPrincipal, (String) row.get("ccy_cd"), (BigDecimal) row.get("active_principal"));
        }

        List<BankCollateral> owned = collaterals.findByOwner_CustId(custId);
        long registered = owned.stream().filter(c -> "REGISTERED".equalsIgnoreCase(c.getCollateralSts())).count();
        long inactive = owned.stream().filter(c -> "INACTIVE".equalsIgnoreCase(c.getCollateralSts())).count();
        Map<String, BigDecimal> allocations = new HashMap<>();
        // Query only ACTIVE links owned by this customer; fetch facility currency in the same query.
        for (BankFinancingCollateral link : links.findActiveFacilityAllocationsForOwner(custId)) {
            add(allocations, link.getFacility().getCcyCd(), link.getAllocatedAmount());
        }

        return new CustomerOverviewResponse(
                customer.getCustId(), customer.getCustNm(), customer.getCustTyp(), customer.getSts(),
                new CustomerOverviewResponse.AccountSummary(customerAccounts.size(), activeAccounts,
                        currencyAmounts(ledger), currencyAmounts(available)),
                new CustomerOverviewResponse.FinancingSummary(customerFacilities.size(), activeFacilities,
                        currencyAmounts(approved)),
                new CustomerOverviewResponse.FixedDepositSummary(fdTotal, fdActive, currencyAmounts(fdPrincipal)),
                new CustomerOverviewResponse.CollateralSummary(registered, inactive, currencyAmounts(allocations))
        );
    }
}
