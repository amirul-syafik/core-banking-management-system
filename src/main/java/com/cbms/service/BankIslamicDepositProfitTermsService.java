package com.cbms.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Records and independently reviews proposed Islamic deposit profit terms.
 * This service NEVER executes a commodity transaction, books profit, places funds,
 * changes deposit status, or posts a GL entry. Review approval is non-operational.
 */
@Service
public class BankIslamicDepositProfitTermsService {
    private final JdbcTemplate jdbc;
    private BankApprovalNotificationService notifications;

    public record Proposal(BigDecimal proposedProfitAmount, BigDecimal expectedAnnualRatePct,
                           BigDecimal customerProfitSharePct, String evidenceReference, String basisNotes) {}
    public record Decision(String decision, String reviewReference, String remarks) {}

    public BankIslamicDepositProfitTermsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Autowired
    public void setNotificationService(BankApprovalNotificationService notifications) {
        this.notifications = notifications;
    }

    /** History is retained, including REJECTED proposals. Latest proposal first. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long fdId) {
        validId(fdId, "Deposit");
        return jdbc.queryForList("""
                SELECT t.terms_id, t.fd_id, t.profile_id, t.contract_typ, t.profit_model,
                       t.principal_snapshot_amt, t.proposed_profit_amt, t.proposed_sale_price,
                       t.expected_annual_rate_pct, t.customer_profit_share_pct,
                       t.evidence_ref, t.basis_notes, t.terms_sts,
                       t.crt_by, t.crt_dt, t.submitted_by, t.submitted_at,
                       t.decided_by, t.decided_at, t.review_reference, t.review_remarks
                FROM public.bank_islamic_deposit_profit_terms t
                WHERE t.fd_id = ? ORDER BY t.terms_id DESC
                """, fdId);
    }

    @Transactional
    public Map<String, Object> create(Long fdId, Proposal input, Authentication actor) {
        validId(fdId, "Deposit");
        if (input == null) throw badRequest("Profit terms are required");
        String maker = username(actor);
        // Lock the FD to serialize competing proposals. Every proposal references an
        // immutable, approved contract snapshot from the configuration migration.
        List<Map<String, Object>> matches = jdbc.queryForList("""
                SELECT fd.fd_id, fd.fd_sts, fd.principal_amt, snap.profile_id,
                       snap.contract_typ, snap.profit_model
                FROM public.bank_fixed_deposit fd
                JOIN public.bank_islamic_deposit_contract_snapshot snap ON snap.fd_id = fd.fd_id
                WHERE fd.fd_id = ? FOR UPDATE OF fd
                """, fdId);
        if (matches.isEmpty()) throw conflict("An Islamic deposit DRAFT with an approved contract snapshot is required");
        Map<String, Object> deposit = matches.get(0);
        if (!"DRAFT".equals(deposit.get("fd_sts"))) {
            throw conflict("Profit terms can only be proposed for a DRAFT Islamic deposit");
        }
        String type = Objects.toString(deposit.get("contract_typ"), "");
        String model = Objects.toString(deposit.get("profit_model"), "");
        BigDecimal principal = (BigDecimal) deposit.get("principal_amt");
        if (principal == null || principal.signum() <= 0) throw conflict("Deposit principal is invalid");

        String evidence = requireText(input.evidenceReference(), "Supporting document reference", 4, 160);
        String notes = requireText(input.basisNotes(), "Pricing basis and assumptions", 20, 1000);
        BigDecimal proposedProfit = null;
        BigDecimal proposedSalePrice = null;
        BigDecimal expectedRate = null;
        BigDecimal sharePct = null;
        switch (type) {
            case "TAWARRUQ" -> {
                if (!"DEFERRED_SALE".equals(model)) throw conflict("Inconsistent contract profit model");
                requireAbsent(input.expectedAnnualRatePct(), input.customerProfitSharePct());
                proposedProfit = money(input.proposedProfitAmount(), "Proposed deferred-sale profit", false);
                proposedSalePrice = principal.add(proposedProfit);
                if (proposedSalePrice.precision() > 20) throw badRequest("Proposed sale price is too large");
            }
            case "WAKALAH" -> {
                if (!"AGENCY".equals(model)) throw conflict("Inconsistent contract profit model");
                requireAbsent(input.proposedProfitAmount(), input.customerProfitSharePct());
                expectedRate = percent(input.expectedAnnualRatePct(), "Expected annual profit rate", true);
            }
            case "MUDARABAH" -> {
                if (!"PROFIT_SHARING".equals(model)) throw conflict("Inconsistent contract profit model");
                requireAbsent(input.proposedProfitAmount(), input.expectedAnnualRatePct());
                sharePct = percent(input.customerProfitSharePct(), "Customer profit-sharing ratio", false);
            }
            default -> throw conflict("Unsupported Islamic deposit contract: " + type);
        }

        Long id = jdbc.queryForObject("""
                INSERT INTO public.bank_islamic_deposit_profit_terms
                (fd_id, profile_id, contract_typ, profit_model, principal_snapshot_amt,
                 proposed_profit_amt, proposed_sale_price, expected_annual_rate_pct,
                 customer_profit_share_pct, evidence_ref, basis_notes, crt_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING terms_id
                """, Long.class, fdId, deposit.get("profile_id"), type, model, principal,
                proposedProfit, proposedSalePrice, expectedRate, sharePct, evidence, notes, maker);
        return get(id);
    }

    @Transactional
    public Map<String, Object> submit(Long id, Authentication actor) {
        String maker = username(actor);
        Map<String, Object> terms = lock(id);
        if (!"DRAFT".equals(terms.get("terms_sts"))) throw conflict("Only DRAFT terms can be submitted");
        if (!maker.equalsIgnoreCase(Objects.toString(terms.get("crt_by"), ""))) {
            throw conflict("Only the original maker may submit these terms");
        }
        if (!"DRAFT".equals(terms.get("fd_sts"))) throw conflict("Deposit must remain DRAFT");
        if (jdbc.update("""
                UPDATE public.bank_islamic_deposit_profit_terms
                SET terms_sts = 'SUBMITTED', submitted_by = ?, submitted_at = CURRENT_TIMESTAMP
                WHERE terms_id = ? AND terms_sts = 'DRAFT'
                """, maker, id) != 1) throw conflict("Proposal was already processed");
        if (notifications != null) notifications.notifyRole("ADMIN", maker, "FIXED_DEPOSITS", "FD_ISLAMIC_TERMS", id,
                "PENDING", "Islamic deposit profit terms review required",
                "Profit terms #" + id + " require an independent reviewer. This is not placement authorization.");
        return get(id);
    }

    @Transactional
    public Map<String, Object> decide(Long id, Decision input, Authentication actor) {
        String checker = username(actor);
        if (input == null) throw badRequest("Review decision is required");
        String decision = Objects.toString(input.decision(), "").trim().toUpperCase(Locale.ROOT);
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            throw badRequest("Decision must be APPROVE or REJECT");
        }
        Map<String, Object> terms = lock(id);
        if (!"SUBMITTED".equals(terms.get("terms_sts"))) throw conflict("Only SUBMITTED terms can be decided");
        if (!"DRAFT".equals(terms.get("fd_sts"))) throw conflict("Deposit must remain DRAFT");
        if (checker.equalsIgnoreCase(Objects.toString(terms.get("crt_by"), ""))) {
            throw conflict("Maker cannot review their own proposed profit terms");
        }
        String reference = Objects.toString(input.reviewReference(), "").trim();
        String remarks = Objects.toString(input.remarks(), "").trim();
        if (remarks.length() > 1000) throw badRequest("Review remarks are too long");
        if ("APPROVE".equals(decision)) {
            if (reference.length() < 4 || reference.length() > 160) {
                throw badRequest("An external pricing approval document reference is required");
            }
        } else if (remarks.length() < 5) {
            throw badRequest("Enter a rejection reason of at least 5 characters");
        }
        String status = "APPROVE".equals(decision) ? "APPROVED" : "REJECTED";
        if (jdbc.update("""
                UPDATE public.bank_islamic_deposit_profit_terms
                SET terms_sts = ?, decided_by = ?, decided_at = CURRENT_TIMESTAMP,
                    review_reference = ?, review_remarks = ?
                WHERE terms_id = ? AND terms_sts = 'SUBMITTED'
                """, status, checker, "APPROVED".equals(status) ? reference : null,
                remarks.isBlank() ? null : remarks, id) != 1) {
            throw conflict("Proposal decision was not applied");
        }
        if (notifications != null) notifications.notifyUser(Objects.toString(terms.get("crt_by"), ""),
                "FIXED_DEPOSITS", "FD_ISLAMIC_TERMS", id, status,
                "Islamic deposit profit terms " + status.toLowerCase(Locale.ROOT),
                "Profit terms #" + id + " were reviewed. Financial processing remains blocked.");
        return get(id);
    }

    private Map<String, Object> get(Long id) {
        List<Map<String, Object>> rows = listById(id, false);
        if (rows.isEmpty()) throw notFound("Profit proposal not found");
        return rows.get(0);
    }
    private Map<String, Object> lock(Long id) {
        List<Map<String, Object>> rows = listById(id, true);
        if (rows.isEmpty()) throw notFound("Profit proposal not found");
        return rows.get(0);
    }
    private List<Map<String, Object>> listById(Long id, boolean lock) {
        validId(id, "Proposal");
        return jdbc.queryForList("""
                SELECT t.terms_id, t.fd_id, t.terms_sts, t.crt_by, fd.fd_sts
                FROM public.bank_islamic_deposit_profit_terms t
                JOIN public.bank_fixed_deposit fd ON fd.fd_id = t.fd_id
                WHERE t.terms_id = ?
                """ + (lock ? " FOR UPDATE OF t, fd" : ""), id);
    }

    private String username(Authentication actor) {
        if (actor == null || !actor.isAuthenticated() || actor.getName() == null ||
                actor.getName().isBlank() || "anonymousUser".equals(actor.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return actor.getName();
    }
    private void requireAbsent(BigDecimal... values) {
        for (BigDecimal v : values) if (v != null) throw badRequest("Profit inputs are incompatible with contract type");
    }
    private BigDecimal money(BigDecimal value, String field, boolean allowZero) {
        if (value == null || value.scale() > 2 || value.precision() > 20 ||
                (allowZero ? value.signum() < 0 : value.signum() <= 0)) {
            throw badRequest(field + " must be a positive amount with up to 2 decimal places");
        }
        return value;
    }
    private BigDecimal percent(BigDecimal value, String field, boolean allowZero) {
        if (value == null || value.scale() > 5 || value.precision() > 9 ||
                (allowZero ? value.signum() < 0 : value.signum() <= 0) ||
                value.compareTo(new BigDecimal("100")) > 0) {
            throw badRequest(field + " must be within the permitted percentage range");
        }
        return value;
    }
    private String requireText(String value, String field, int min, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() < min || trimmed.length() > max) {
            throw badRequest(field + " must contain " + min + " to " + max + " characters");
        }
        return trimmed;
    }
    private void validId(Long id, String label) {
        if (id == null || id <= 0) throw badRequest(label + " ID is required");
    }
    private ResponseStatusException badRequest(String msg) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);}
    private ResponseStatusException conflict(String msg) {return new ResponseStatusException(HttpStatus.CONFLICT,msg);}
    private ResponseStatusException notFound(String msg) {return new ResponseStatusException(HttpStatus.NOT_FOUND,msg);}
}
