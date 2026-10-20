package com.cbms.service;

import com.cbms.dto.CustomerRelationshipsResponse;
import com.cbms.entity.*;
import com.cbms.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@Service
public class CustomerRelationshipsService {
    private final CustomerRepository customers;
    private final BankAccountRepository accounts;
    private final BankAccountBalanceRepository balances;
    private final BankFinancingFacilityRepository facilities;
    private final JdbcTemplate jdbc;

    public CustomerRelationshipsService(CustomerRepository customers, BankAccountRepository accounts, BankAccountBalanceRepository balances, BankFinancingFacilityRepository facilities, JdbcTemplate jdbc) {
        this.customers = customers;
        this.accounts = accounts;
        this.balances = balances;
        this.facilities = facilities;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CustomerRelationshipsResponse get(Long id) {
        Customer customer = customers.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        var accountRows = accounts.findByCustIdOrderByAcctIdDesc(id).stream().map(a -> {
            var b = balances.findById(a.getAcctId()).orElse(null);
            return new CustomerRelationshipsResponse.Account(a.getAcctId(), a.getAcctNo(), a.getAcctTyp(), a.getCcyCd(), a.getAcctSts(), a.getOpenDt(), b == null ? null : b.getLedgerBal(), b == null ? null : b.getAvailBal());
        }).toList();
        var facilityRows = facilities.findByCustIdOrderByFacIdDesc(id).stream().map(f -> new CustomerRelationshipsResponse.Facility(f.getFacId(), f.getFacRef(), f.getApplId(), f.getFacTyp(), f.getCcyCd(), f.getApprAmt(), f.getTenorMth(), f.getEffRate(), f.getFacSts(), f.getBookDt(), f.getMaturityDt())).toList();
        var deposits = jdbc.query(""" 
                SELECT fd_id,fd_ref,fd_typ,ccy_cd,principal_amt,tenor_mth,placement_dt,maturity_dt,interest_rate,fd_sts
                FROM public.bank_fixed_deposit WHERE cust_id=? ORDER BY fd_id DESC
                """, (rs, n) -> new CustomerRelationshipsResponse.Deposit(rs.getLong("fd_id"), rs.getString("fd_ref"), rs.getString("fd_typ"), rs.getString("ccy_cd"), rs.getBigDecimal("principal_amt"), rs.getInt("tenor_mth"), rs.getDate("placement_dt") == null ? null : rs.getDate("placement_dt").toLocalDate(), rs.getDate("maturity_dt") == null ? null : rs.getDate("maturity_dt").toLocalDate(), rs.getBigDecimal("interest_rate"), rs.getString("fd_sts")), id);
        var vehicles = jdbc.query("""
                SELECT vehicle_id,registration_no,chassis_no,vehicle_make,vehicle_model,manufacture_year,vehicle_sts
                FROM public.bank_vehicle WHERE owner_cust_id=? ORDER BY vehicle_id DESC
                """, (rs, n) -> new CustomerRelationshipsResponse.Vehicle(rs.getLong("vehicle_id"), rs.getString("registration_no"), rs.getString("chassis_no"), rs.getString("vehicle_make"), rs.getString("vehicle_model"), (Integer) rs.getObject("manufacture_year"), rs.getString("vehicle_sts")), id);
        var collaterals = jdbc.query("""
                SELECT c.collateral_id,c.collateral_ref,c.collateral_typ,c.collateral_desc,c.collateral_sts,c.vehicle_id,
                (SELECT v.forced_sale_value FROM public.bank_collateral_valuation v WHERE v.collateral_id=c.collateral_id ORDER BY v.valuation_dt DESC,v.valuation_id DESC LIMIT 1) AS forced_sale_value,
                (SELECT COALESCE(SUM(l.allocated_amount),0) FROM public.bank_financing_collateral l WHERE l.collateral_id=c.collateral_id AND l.link_sts='ACTIVE') AS active_allocation
                FROM public.bank_collateral c WHERE c.owner_cust_id=? ORDER BY c.collateral_id DESC
                """, (rs, n) -> new CustomerRelationshipsResponse.Collateral(rs.getLong("collateral_id"), rs.getString("collateral_ref"), rs.getString("collateral_typ"), rs.getString("collateral_desc"), rs.getString("collateral_sts"), (Long) rs.getObject("vehicle_id"), rs.getBigDecimal("forced_sale_value"), rs.getBigDecimal("active_allocation")), id);
        return new CustomerRelationshipsResponse(id, customer.getCustNm(), accountRows, facilityRows, deposits, vehicles, collaterals);
    }
}
