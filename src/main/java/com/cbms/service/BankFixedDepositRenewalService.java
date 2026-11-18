package com.cbms.service;

import com.cbms.reference.BusinessReferenceGenerator;

import com.cbms.dto.BankFixedDepositRenewalRequest;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class BankFixedDepositRenewalService {

    private final JdbcTemplate jdbc;
    private final BankFixedDepositMaturityService maturityService;
    private final BankFloatingDepositRateService floatingRates;

    /** Compatibility for existing direct-instantiation tests. */
    public BankFixedDepositRenewalService(JdbcTemplate jdbc, BankFixedDepositMaturityService maturityService) {
        this(jdbc, maturityService, new BankFloatingDepositRateService(jdbc));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public BankFixedDepositRenewalService(JdbcTemplate jdbc, BankFixedDepositMaturityService maturityService,
                                          BankFloatingDepositRateService floatingRates) {
        this.jdbc = jdbc;
        this.maturityService = maturityService;
        this.floatingRates = floatingRates;
    }

    @Transactional
    public Map<String, Object> renew(
            Long fdId,
            BankFixedDepositRenewalRequest request,
            String username
    ) {
        if (fdId == null || fdId <= 0 || request == null) {
            throw badRequest("Invalid renewal request");
        }

        boolean capitalize = "PRINCIPAL_PLUS_INTEREST".equals(request.renewalType());
        if (!capitalize && !"PRINCIPAL_ONLY".equals(request.renewalType())) {
            throw badRequest("Unsupported FD renewal type");
        }

        // Lock original FD to serialize renewal requests.
        List<Map<String, Object>> deposits = jdbc.queryForList(
                """
                        SELECT *
                        FROM public.bank_fixed_deposit
                        WHERE fd_id = ?
                        FOR UPDATE
                        """,
                fdId
        );

        if (deposits.isEmpty()) {
            throw notFound("Fixed deposit not found");
        }

        Map<String, Object> original = deposits.get(0);

        BankIslamicDepositGuard.requireConventional(jdbc, fdId, "renewal");

        if (!"MATURED".equals(original.get("fd_sts"))) {
            throw conflict("Only MATURED fixed deposits can be renewed");
        }

        Integer maturityCount = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.bank_fixed_deposit_maturity
                        WHERE fd_id = ?
                          AND maturity_sts = 'PROCESSED'
                        """,
                Integer.class,
                fdId
        );

        if (maturityCount == null || maturityCount != 1) {
            throw conflict("FD maturity processing is not verified");
        }

        Integer renewalCount = jdbc.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM public.bank_fixed_deposit_renewal
                        WHERE original_fd_id = ?
                        """,
                Integer.class,
                fdId
        );

        if (renewalCount != null && renewalCount > 0) {
            throw conflict("Fixed deposit has already been renewed");
        }

        BigDecimal principal =
                (BigDecimal) original.get("principal_amt");

        // Interest already disbursed is never capitalized a second time.
        // Lock the posted accruals while deciding which remain unpaid.
        List<Map<String, Object>> unpaidAccruals = jdbc.queryForList(
                """
                        SELECT a.accrual_id, a.accrued_interest,
                               EXISTS (
                                   SELECT 1 FROM public.bank_transaction t
                                   JOIN public.bank_ledger_entry e ON e.txn_id = t.txn_id
                                   JOIN public.bank_ledger_account g ON g.gl_id = e.gl_id
                                   WHERE t.txn_ref = a.posting_ref
                                     AND t.txn_typ = 'FD_INTEREST_ACCRUAL'
                                     AND t.txn_sts = 'POSTED'
                                     AND t.txn_amt = a.accrued_interest
                                     AND g.gl_cd = '200300'
                                     AND e.entry_side = 'CREDIT'
                                     AND e.entry_amt = a.accrued_interest
                               ) AS posting_verified
                        FROM public.bank_fixed_deposit_accrual a
                        WHERE a.fd_id = ? AND a.accrual_sts = 'POSTED'
                          AND NOT EXISTS (
                              SELECT 1 FROM public.bank_fixed_deposit_interest_payout_item i
                              WHERE i.accrual_id = a.accrual_id
                          )
                        ORDER BY a.accrual_id
                        FOR UPDATE OF a
                        """, fdId);
        BigDecimal capitalizedInterest = BigDecimal.ZERO;
        if (capitalize) {
            for (Map<String, Object> accrual : unpaidAccruals) {
                BigDecimal interest = (BigDecimal) accrual.get("accrued_interest");
                if (interest.signum() > 0
                        && !Boolean.TRUE.equals(accrual.get("posting_verified"))) {
                    throw conflict("Unpaid interest accrual GL posting is not verified");
                }
                capitalizedInterest = capitalizedInterest.add(interest);
            }
            if (capitalizedInterest.signum() <= 0) {
                throw conflict("No unpaid positive interest to capitalize");
            }
            if (capitalizedInterest.scale() > 2) {
                throw conflict("Accrued interest precision is invalid");
            }
        }
        if (!capitalize && unpaidAccruals.stream()
                .map(a -> (BigDecimal) a.get("accrued_interest"))
                .reduce(BigDecimal.ZERO, BigDecimal::add).signum() > 0) {
            throw conflict("Pay unpaid interest before principal-only renewal");
        }
        BigDecimal renewalAmount = principal.add(capitalizedInterest);
        if (renewalAmount.precision() - renewalAmount.scale() > 17) {
            throw conflict("Renewal amount exceeds supported precision");
        }

        Long customerId =
                ((Number) original.get("cust_id")).longValue();

        Long productId =
                ((Number) original.get("prod_id")).longValue();

        String currency = (String) original.get("ccy_cd");

        if (!"MYR".equals(currency)) {
            throw conflict("Only MYR renewal is supported");
        }

        // Verify original FD funding.
        List<Map<String, Object>> funding = jdbc.queryForList(
                """
                        SELECT p.source_acct_id,
                               p.placement_amt,
                               p.funding_typ,
                               t.txn_id
                        FROM public.bank_fixed_deposit_placement p
                        JOIN public.bank_transaction t
                          ON t.txn_ref = p.posting_ref
                        WHERE p.fd_id = ?
                          AND p.placement_sts = 'POSTED'
                          AND p.placement_amt = ?
                          AND p.ccy_cd = 'MYR'
                          AND t.txn_typ = p.funding_typ
                          AND t.txn_sts = 'POSTED'
                          AND t.txn_amt = p.placement_amt
                          AND t.acct_id = p.source_acct_id
                          AND t.ccy_cd = p.ccy_cd
                          AND EXISTS (
                              SELECT 1
                              FROM public.bank_ledger_entry e
                              JOIN public.bank_ledger_account g
                                ON g.gl_id = e.gl_id
                              WHERE e.txn_id = t.txn_id
                                AND g.gl_cd = '200200'
                                AND e.entry_side = 'CREDIT'
                                AND e.entry_amt = p.placement_amt
                          )
                        """,
                fdId,
                principal
        );

        if (funding.size() != 1) {
            throw conflict("Original FD funding is not verified");
        }

        Long sourceAccountId =
                ((Number) funding.get(0).get("source_acct_id"))
                        .longValue();

        // Confirm the customer remains active.
        List<String> customerStatuses = jdbc.queryForList(
                """
                        SELECT sts
                        FROM public.customer_master
                        WHERE cust_id = ?
                        """,
                String.class,
                customerId
        );

        if (customerStatuses.size() != 1
                || !"ACTIVE".equals(customerStatuses.get(0))) {
            throw conflict("Customer must be ACTIVE");
        }

        // Validate scheme and product.
        List<Map<String, Object>> schemes = jdbc.queryForList(
                """
                        SELECT s.*, p.prod_cat, p.prod_sts, p.ccy_cd, p.banking_typ
                        FROM public.bank_scheme s
                        JOIN public.bank_product p
                          ON p.prod_id = s.prod_id
                        WHERE s.schm_id = ?
                        """,
                request.schemeId()
        );

        if (schemes.isEmpty()) {
            throw notFound("Scheme not found");
        }

        Map<String, Object> scheme = schemes.get(0);

        if (!"ACTIVE".equals(scheme.get("schm_sts"))
                || !"ACTIVE".equals(scheme.get("prod_sts"))) {
            throw conflict("Scheme and product must be ACTIVE");
        }

        if (((Number) scheme.get("prod_id")).longValue()
                != productId) {
            throw conflict("Renewal scheme must belong to original product");
        }

        if (!"MYR".equals(scheme.get("ccy_cd"))) {
            throw conflict("Only MYR schemes are supported");
        }

        BigDecimal minAmount = (BigDecimal) scheme.get("min_amt");
        BigDecimal maxAmount = (BigDecimal) scheme.get("max_amt");

        if (renewalAmount.compareTo(minAmount) < 0
                || renewalAmount.compareTo(maxAmount) > 0) {
            throw conflict("Renewal principal is outside scheme limits");
        }

        int minTenor = ((Number) scheme.get("min_tenor")).intValue();
        int maxTenor = ((Number) scheme.get("max_tenor")).intValue();

        if (request.tenorMonths() < minTenor
                || request.tenorMonths() > maxTenor) {
            throw conflict("Renewal tenor is outside scheme limits");
        }

        LocalDate renewalDate = LocalDate.now();

        List<Map<String, Object>> rates = jdbc.queryForList(
                """
                        SELECT rate_typ, fixed_rate_pct, min_rate_pct, max_rate_pct,
                               base_rate_cd, margin_pct
                        FROM public.bank_scheme_rate
                        WHERE schm_id = ? AND rate_sts = 'ACTIVE' AND eff_dt <= ?
                        ORDER BY eff_dt DESC, schm_rate_id DESC LIMIT 1
                        """, request.schemeId(), renewalDate);
        if (rates.isEmpty()) throw conflict("No applicable scheme rate for renewal date");
        var rateConfig = rates.get(0);
        String rateType = (String) rateConfig.get("rate_typ");
        String baseCode = null;
        BigDecimal margin = null;
        BigDecimal interestRate;
        if ("FIXED".equals(rateType)) {
            BigDecimal standardRate = (BigDecimal) rateConfig.get("fixed_rate_pct");
            if (standardRate == null) throw conflict("Fixed scheme has no configured rate");
            BigDecimal minimumRate = (BigDecimal) rateConfig.get("min_rate_pct");
            BigDecimal maximumRate = (BigDecimal) rateConfig.get("max_rate_pct");
            interestRate = request.interestRate() == null ? standardRate : request.interestRate();
            if (interestRate.signum() < 0 ||
                    interestRate.compareTo(minimumRate == null ? standardRate : minimumRate) < 0 ||
                    interestRate.compareTo(maximumRate == null ? standardRate : maximumRate) > 0)
                throw conflict("Selected renewal rate is outside the current scheme range");
        } else if ("FLOATING".equals(rateType)) {
            if ("ISLAMIC".equals(scheme.get("banking_typ")))
                throw conflict("Islamic floating term deposit requires a configured Shariah profit contract");
            baseCode = (String) rateConfig.get("base_rate_cd");
            margin = (BigDecimal) rateConfig.get("margin_pct");
            interestRate = floatingRates.rateOn(baseCode, margin, renewalDate);
            if (request.interestRate() != null && request.interestRate().compareTo(interestRate) != 0)
                throw conflict("Selected floating renewal rate no longer matches the current reference rate");
        } else throw conflict("Unsupported renewal scheme rate type");

        LocalDate maturityDate = maturityService.calculate(
                renewalDate,
                request.tenorMonths(),
                "NONE"
        ).contractualMaturityDate();

        Long fixedDepositGl = jdbc.queryForObject(
                """
                        SELECT gl_id
                        FROM public.bank_ledger_account
                        WHERE gl_cd = '200200'
                          AND ccy_cd = 'MYR'
                          AND act_flg = TRUE
                        """,
                Long.class
        );

        if (fixedDepositGl == null) {
            throw conflict("Fixed Deposit GL 200200 is unavailable");
        }

        Long payableGl = null;
        if (capitalize) {
            List<Long> payableIds = jdbc.queryForList(
                    """
                            SELECT gl_id FROM public.bank_ledger_account
                            WHERE gl_cd = '200300' AND ccy_cd = 'MYR' AND act_flg = TRUE
                            """, Long.class);
            if (payableIds.size() != 1) {
                throw conflict("Interest payable GL 200300 is unavailable");
            }
            payableGl = payableIds.get(0);
        }

        String newFdReference = BusinessReferenceGenerator.next(jdbc, BusinessReferenceGenerator.Stream.FIXED_DEPOSIT);
        String renewalReference = BusinessReferenceGenerator.next(jdbc, BusinessReferenceGenerator.Stream.FD_RENEWAL);
        String transactionReference = BusinessReferenceGenerator.next(jdbc, BusinessReferenceGenerator.Stream.TRANSACTION);
        String placementReference = BusinessReferenceGenerator.next(jdbc, BusinessReferenceGenerator.Stream.FD_PLACEMENT);

        // Create renewed FD without debiting a savings account.
        Long renewedFdId = jdbc.queryForObject(
                """
                        INSERT INTO public.bank_fixed_deposit
                        (
                            fd_ref, cust_id, prod_id, schm_id,
                            fd_typ, principal_amt, tenor_mth,
                            placement_dt, maturity_dt, interest_rate,
                            rate_typ, base_rate_cd, margin_pct, fd_sts, remarks, crt_by
                        )
                        VALUES (
                            ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                            ?, ?, ?, 'ACTIVE', ?, ?
                        )
                        RETURNING fd_id
                        """,
                Long.class,
                newFdReference,
                customerId,
                productId,
                request.schemeId(),
                original.get("fd_typ"),
                renewalAmount,
                request.tenorMonths(),
                renewalDate,
                maturityDate,
                interestRate,
                rateType,
                baseCode,
                margin,
                request.remarks(),
                username
        );

        // Record the internal renewal transaction.
        Long transactionId = jdbc.queryForObject(
                """
                        INSERT INTO public.bank_transaction
                        (
                            txn_ref, acct_id, txn_typ, txn_amt,
                            ccy_cd, txn_sts, idempotency_key, crt_by
                        )
                        VALUES (
                            ?, ?, 'FD_RENEWAL', ?,
                            'MYR', 'POSTED', ?, ?
                        )
                        RETURNING txn_id
                        """,
                Long.class,
                transactionReference,
                sourceAccountId,
                renewalAmount,
                "FD-RENEWAL:" + fdId,
                username
        );

        // Debit original FD liability.
        jdbc.update(
                """
                        INSERT INTO public.bank_ledger_entry
                        (txn_id, gl_id, acct_id, entry_side, entry_amt)
                        VALUES (?, ?, NULL, 'DEBIT', ?)
                        """,
                transactionId,
                fixedDepositGl,
                principal
        );

        // Convert unpaid interest payable into the renewed FD liability.
        if (capitalize) {
            jdbc.update(
                    """
                            INSERT INTO public.bank_ledger_entry
                            (txn_id, gl_id, acct_id, entry_side, entry_amt)
                            VALUES (?, ?, NULL, 'DEBIT', ?)
                            """, transactionId, payableGl, capitalizedInterest);
        }

        // Credit renewed FD liability.
        jdbc.update(
                """
                        INSERT INTO public.bank_ledger_entry
                        (txn_id, gl_id, acct_id, entry_side, entry_amt)
                        VALUES (?, ?, NULL, 'CREDIT', ?)
                        """,
                transactionId,
                fixedDepositGl,
                renewalAmount
        );

        // Register the renewed FD's genuine internal funding.
        jdbc.update(
                """
                        INSERT INTO public.bank_fixed_deposit_placement
                        (
                            placement_ref, fd_id, source_acct_id,
                            placement_amt, ccy_cd, placement_dt,
                            posting_ref, placement_sts, funding_typ,
                            remarks, crt_by
                        )
                        VALUES (
                            ?, ?, ?, ?, 'MYR', ?,
                            ?, 'POSTED', 'FD_RENEWAL', ?, ?
                        )
                        """,
                placementReference,
                renewedFdId,
                sourceAccountId,
                renewalAmount,
                renewalDate,
                transactionReference,
                "Internally funded by FD renewal",
                username
        );

        jdbc.update(
                """
                        INSERT INTO public.bank_fixed_deposit_renewal
                        (
                            renewal_ref, original_fd_id, renewed_fd_id,
                            renewal_typ, principal_amt,
                            capitalized_interest, renewal_amt,
                            renewal_dt, renewal_sts, remarks, crt_by
                        )
                        VALUES (
                            ?, ?, ?, ?, ?,
                            ?, ?, ?, 'PROCESSED', ?, ?
                        )
                        """,
                renewalReference,
                fdId,
                renewedFdId,
                request.renewalType(),
                principal,
                capitalizedInterest,
                renewalAmount,
                renewalDate,
                request.remarks(),
                username
        );

        return Map.ofEntries(
                Map.entry("originalFdId", fdId),
                Map.entry("renewedFdId", renewedFdId),
                Map.entry("renewalReference", renewalReference),
                Map.entry("renewedFdReference", newFdReference),
                Map.entry("renewalType", request.renewalType()),
                Map.entry("principalAmount", principal),
                Map.entry("capitalizedInterest", capitalizedInterest),
                Map.entry("renewalAmount", renewalAmount),
                Map.entry("renewalDate", renewalDate.toString()),
                Map.entry("maturityDate", maturityDate.toString()),
                Map.entry("status", "ACTIVE")
        );
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST, message
        );
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND, message
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, message
        );
    }
}
