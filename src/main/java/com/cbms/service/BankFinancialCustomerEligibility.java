package com.cbms.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Central eligibility requirements for NEW banking exposure/funding operations.
 *
 * Draft applications remain permitted under their existing workflows. Repayments,
 * corrective reversals and servicing of existing liabilities must NOT use this
 * guard, because suspending a customer must not prevent recovery or correction.
 *
 * Currently only active MYR SAVINGS/CURRENT accounts are considered suitable.
 * This is the CBMS demo policy; real-bank eligibility must be policy-approved.
 */
public final class BankFinancialCustomerEligibility {

    private BankFinancialCustomerEligibility() {
    }

    /** Fail closed when the customer, latest KYC or identification is ineligible. */
    public static void requireReadyForNewExposure(JdbcTemplate jdbc, Long customerId) {
        if (customerId == null) {
            throw conflict("An eligible customer is required");
        }

        List<String> statuses = jdbc.queryForList(
                "SELECT sts FROM public.customer_master WHERE cust_id = ? FOR SHARE",
                String.class, customerId);
        if (statuses.isEmpty() || !"ACTIVE".equals(statuses.get(0))) {
            throw conflict("Customer must be ACTIVE before financial execution");
        }

        // Take the latest assessment, not any older approved record. Invalid and
        // expired assessments cannot be bypassed by an earlier VERIFIED row.
        List<Map<String, Object>> kyc = jdbc.queryForList(
                """
                SELECT asmt_sts,
                       (rev_dt IS NOT NULL AND rev_dt <= CURRENT_DATE
                        AND nxt_rev_dt IS NOT NULL AND nxt_rev_dt >= CURRENT_DATE) AS kyc_current
                FROM public.customer_kyc
                WHERE cust_id = ?
                ORDER BY upd_dt DESC NULLS LAST, kyc_id DESC
                LIMIT 1
                FOR SHARE
                """, customerId);
        if (kyc.isEmpty()) {
            throw conflict("Current verified KYC assessment is required");
        }
        Map<String, Object> latestKyc = kyc.get(0);
        if (!"VERIFIED".equals(latestKyc.get("asmt_sts"))
                || !Boolean.TRUE.equals(latestKyc.get("kyc_current"))) {
            throw conflict("Latest customer KYC must be VERIFIED and within review dates");
        }

        Integer identificationCount = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM public.customer_identification
                WHERE cust_id = ? AND doc_no IS NOT NULL
                  AND BTRIM(doc_no) <> ''
                  AND (exp_dt IS NULL OR exp_dt >= CURRENT_DATE)
                """, Integer.class, customerId);
        if (identificationCount == null || identificationCount < 1) {
            throw conflict("Customer requires a valid identification document");
        }
    }

    /** At least one eligible operating account owned by this customer. */
    public static void requireActiveOperatingAccount(JdbcTemplate jdbc, Long customerId) {
        if (customerId == null) {
            throw conflict("An eligible customer is required");
        }
        // Shared row lock prevents a concurrent account-status change from
        // invalidating this check before the financial transaction commits.
        List<Long> accounts = jdbc.queryForList(
                """
                SELECT acct_id FROM public.bank_account
                WHERE cust_id = ?
                  AND acct_sts = 'ACTIVE'
                  AND acct_typ IN ('SAVINGS', 'CURRENT')
                  AND ccy_cd = 'MYR'
                  AND open_dt <= CURRENT_DATE
                  AND (close_dt IS NULL OR close_dt > CURRENT_DATE)
                ORDER BY acct_id
                LIMIT 1 FOR SHARE
                """, Long.class, customerId);
        if (accounts.isEmpty()) {
            throw conflict("Customer requires an ACTIVE MYR savings/current account before financial execution");
        }
    }

    /** Validates exact funding account ownership/eligibility; funding adapter checks again under row lock. */
    public static void requireOwnedOperatingAccount(JdbcTemplate jdbc, Long customerId, Long accountId) {
        if (customerId == null || accountId == null || accountId <= 0) {
            throw conflict("An eligible source account is required");
        }
        List<Long> accounts = jdbc.queryForList(
                """
                SELECT acct_id FROM public.bank_account
                WHERE acct_id = ? AND cust_id = ?
                  AND acct_sts = 'ACTIVE'
                  AND acct_typ IN ('SAVINGS', 'CURRENT')
                  AND ccy_cd = 'MYR'
                  AND open_dt <= CURRENT_DATE
                  AND (close_dt IS NULL OR close_dt > CURRENT_DATE)
                FOR SHARE
                """, Long.class, accountId, customerId);
        if (accounts.size() != 1) {
            throw conflict("Source account must be an ACTIVE MYR savings/current account owned by the customer");
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
