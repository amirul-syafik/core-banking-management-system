package com.cbms.service;

import com.cbms.reference.BusinessReferenceGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Non-posting review of proposed Islamic deposit GL codes. Never moves funds or posts journals. */
@Service
public class BankIslamicDepositLedgerMappingReviewService {
    private final JdbcTemplate jdbc;
    private BankApprovalNotificationService notifications;

    public BankIslamicDepositLedgerMappingReviewService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Autowired public void setNotifications(BankApprovalNotificationService notifications) {
        this.notifications = notifications;
    }

    public record Proposal(String placementDebitGl, String placementCreditGl,
                           String accrualDebitGl, String accrualCreditGl,
                           String payoutDebitGl, String payoutCreditGl,
                           String maturityDebitGl, String maturityCreditGl) {}
    public record Decision(String decision, String reviewReference, String remarks) {}

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long fdId) {
        requireId(fdId);
        return jdbc.queryForList("""
            SELECT mapping_id, mapping_ref, fd_id, execution_id, contract_typ,
                   accounting_policy_ref, ledger_mapping_ref,
                   placement_debit_gl, placement_credit_gl, accrual_debit_gl, accrual_credit_gl,
                   payout_debit_gl, payout_credit_gl, maturity_debit_gl, maturity_credit_gl,
                   mapping_sts, crt_by, crt_dt, submitted_by, submitted_at,
                   decided_by, decided_at, review_reference, review_remarks
            FROM public.bank_islamic_deposit_ledger_mapping_review
            WHERE fd_id = ? ORDER BY mapping_id DESC
            """, fdId);
    }

    @Transactional
    public Map<String, Object> create(Long fdId, Proposal proposal, Authentication actor) {
        requireId(fdId);
        if (proposal == null) throw badRequest("Ledger mapping proposal is required");
        String maker = username(actor);
        List<Map<String, Object>> evidence = jdbc.queryForList("""
            SELECT e.execution_id, e.contract_typ, e.accounting_policy_ref, e.ledger_mapping_ref,
                   fd.fd_sts
            FROM public.bank_islamic_deposit_execution_review e
            JOIN public.bank_fixed_deposit fd ON fd.fd_id = e.fd_id
            WHERE e.fd_id = ? AND e.review_sts = 'REVIEWED'
            ORDER BY e.execution_id DESC LIMIT 1 FOR UPDATE OF fd
            """, fdId);
        if (evidence.isEmpty()) throw conflict("Independently reviewed Islamic execution evidence is required");
        Map<String, Object> source = evidence.get(0);
        if (!"DRAFT".equals(source.get("fd_sts"))) throw conflict("Only a DRAFT deposit may propose GL mapping");
        List<String> codes = new ArrayList<>();
        codes.add(glCode(proposal.placementDebitGl())); codes.add(glCode(proposal.placementCreditGl()));
        codes.add(glCode(proposal.accrualDebitGl())); codes.add(glCode(proposal.accrualCreditGl()));
        codes.add(glCode(proposal.payoutDebitGl())); codes.add(glCode(proposal.payoutCreditGl()));
        codes.add(glCode(proposal.maturityDebitGl())); codes.add(glCode(proposal.maturityCreditGl()));
        for (int i = 0; i < codes.size(); i += 2) {
            if (codes.get(i).equalsIgnoreCase(codes.get(i + 1)))
                throw badRequest("Debit and credit GL accounts must differ for each proposed event");
        }
        // Only configured, active MYR GL accounts may appear in the proposal.
        Set<String> required = new HashSet<>(codes);
        List<Map<String, Object>> eligible = jdbc.queryForList("""
            SELECT gl_cd FROM public.bank_ledger_account
            WHERE gl_cd IN (?, ?, ?, ?, ?, ?, ?, ?)
              AND ccy_cd = 'MYR' AND act_flg = TRUE
            """, codes.toArray());
        Set<String> found = new HashSet<>();
        for (Map<String, Object> row : eligible) {
            found.add(Objects.toString(row.get("gl_cd"), ""));
        }
        if (!found.containsAll(required)) {
            throw badRequest("All proposed GL codes must exist as active MYR ledger accounts");
        }
        String reference = BusinessReferenceGenerator.next(
                jdbc, BusinessReferenceGenerator.Stream.ISLAMIC_LEDGER_MAPPING);
        Long id = jdbc.queryForObject("""
            INSERT INTO public.bank_islamic_deposit_ledger_mapping_review
             (mapping_ref, fd_id, execution_id, contract_typ, accounting_policy_ref,
              ledger_mapping_ref, placement_debit_gl, placement_credit_gl,
              accrual_debit_gl, accrual_credit_gl, payout_debit_gl, payout_credit_gl,
              maturity_debit_gl, maturity_credit_gl, crt_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING mapping_id
            """, Long.class, reference, fdId, source.get("execution_id"),
                source.get("contract_typ"), source.get("accounting_policy_ref"),
                source.get("ledger_mapping_ref"), codes.get(0), codes.get(1), codes.get(2), codes.get(3),
                codes.get(4), codes.get(5), codes.get(6), codes.get(7), maker);
        return get(id);
    }

    @Transactional
    public Map<String, Object> submit(Long mappingId, Authentication actor) {
        Map<String, Object> row = lock(mappingId);
        String maker = username(actor);
        if (!"DRAFT".equals(row.get("mapping_sts"))) throw conflict("Only DRAFT mapping can be submitted");
        if (!"DRAFT".equals(row.get("fd_sts"))) throw conflict("Deposit is no longer in DRAFT");
        if (!maker.equalsIgnoreCase(Objects.toString(row.get("crt_by"), "")))
            throw conflict("Only the original maker may submit the mapping proposal");
        if (jdbc.update("""
            UPDATE public.bank_islamic_deposit_ledger_mapping_review
            SET mapping_sts = 'SUBMITTED', submitted_by = ?, submitted_at = CURRENT_TIMESTAMP
            WHERE mapping_id = ? AND mapping_sts = 'DRAFT'
            """, maker, mappingId) != 1) throw conflict("Mapping submission failed");
        if (notifications != null) notifications.notifyRole("ADMIN", maker, "FIXED_DEPOSITS", "FD_ISLAMIC_LEDGER_MAPPING",
                mappingId, "PENDING", "Islamic deposit ledger mapping requires review",
                row.get("mapping_ref") + " is awaiting independent review; no GL posting is authorized.");
        return get(mappingId);
    }

    @Transactional
    public Map<String, Object> decide(Long mappingId, Decision input, Authentication actor) {
        if (input == null) throw badRequest("Decision is required");
        String reviewer = username(actor);
        String decision = Objects.toString(input.decision(), "").toUpperCase(Locale.ROOT).trim();
        if (!decision.equals("ACCEPT") && !decision.equals("REJECT"))
            throw badRequest("Decision must be ACCEPT or REJECT");
        Map<String, Object> row = lock(mappingId);
        if (!"SUBMITTED".equals(row.get("mapping_sts"))) throw conflict("Mapping is not submitted");
        if (!"DRAFT".equals(row.get("fd_sts"))) throw conflict("Deposit must remain in DRAFT");
        if (reviewer.equalsIgnoreCase(Objects.toString(row.get("crt_by"), "")))
            throw conflict("Maker cannot approve their own ledger mapping");
        String ref = Objects.toString(input.reviewReference(), "").trim();
        String remarks = Objects.toString(input.remarks(), "").trim();
        if (ref.length() > 160 || remarks.length() > 1000)
            throw badRequest("Review reference or remarks exceed their maximum length");
        if (decision.equals("ACCEPT") && ref.length() < 4)
            throw badRequest("Independent accounting review reference (at least 4 characters) is required");
        if (decision.equals("REJECT") && remarks.length() < 5)
            throw badRequest("Rejection remarks of at least 5 characters are required");
        String status = decision.equals("ACCEPT") ? "REVIEWED" : "REJECTED";
        if (jdbc.update("""
            UPDATE public.bank_islamic_deposit_ledger_mapping_review
            SET mapping_sts = ?, decided_by = ?, decided_at = CURRENT_TIMESTAMP,
                review_reference = ?, review_remarks = ?
            WHERE mapping_id = ? AND mapping_sts = 'SUBMITTED'
            """, status, reviewer, decision.equals("ACCEPT") ? ref : null,
                remarks.isEmpty() ? null : remarks, mappingId) != 1)
            throw conflict("Ledger mapping decision failed");
        if (notifications != null) notifications.notifyUser(Objects.toString(row.get("crt_by"), ""),
                "FIXED_DEPOSITS", "FD_ISLAMIC_LEDGER_MAPPING", mappingId, status,
                "Islamic ledger mapping " + status.toLowerCase(Locale.ROOT),
                "Mapping review recorded; deposit activation and GL posting remain disabled.");
        return get(mappingId);
    }

    private Map<String, Object> get(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT mapping_id, mapping_ref, fd_id, execution_id, contract_typ,
                   accounting_policy_ref, ledger_mapping_ref,
                   placement_debit_gl, placement_credit_gl, accrual_debit_gl, accrual_credit_gl,
                   payout_debit_gl, payout_credit_gl, maturity_debit_gl, maturity_credit_gl,
                   mapping_sts, crt_by, submitted_by, decided_by, review_reference, review_remarks
            FROM public.bank_islamic_deposit_ledger_mapping_review WHERE mapping_id = ?
            """, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger mapping not found");
        return rows.get(0);
    }
    private Map<String, Object> lock(Long id) {
        requireId(id);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT m.mapping_id, m.mapping_ref, m.mapping_sts, m.crt_by, fd.fd_sts
            FROM public.bank_islamic_deposit_ledger_mapping_review m
            JOIN public.bank_fixed_deposit fd ON fd.fd_id = m.fd_id
            WHERE m.mapping_id = ? FOR UPDATE OF m, fd
            """, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger mapping not found");
        return rows.get(0);
    }
    private static String glCode(String value) {
        String trimmed = Objects.toString(value, "").trim();
        if (trimmed.isEmpty() || trimmed.length() > 30 || !trimmed.matches("[A-Za-z0-9_-]+"))
            throw badRequest("Each GL code must be 1–30 letters, digits, dashes or underscores");
        return trimmed;
    }
    private static void requireId(Long id) {
        if (id == null || id <= 0) throw badRequest("A valid record ID is required");
    }
    private static String username(Authentication actor) {
        if (actor == null || !actor.isAuthenticated() || actor.getName() == null || actor.getName().isBlank()
                || "anonymousUser".equals(actor.getName()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        return actor.getName();
    }
    private static ResponseStatusException badRequest(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
    private static ResponseStatusException conflict(String msg) {
        return new ResponseStatusException(HttpStatus.CONFLICT, msg);
    }
}
