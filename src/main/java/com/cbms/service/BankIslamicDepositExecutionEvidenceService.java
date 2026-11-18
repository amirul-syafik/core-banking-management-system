package com.cbms.service;

import com.cbms.reference.BusinessReferenceGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Independently reviews documentary evidence and accounting configuration.
 * A reviewed record is NOT proof of a commodity trade, a settled investment,
 * financial activation, a GL posting, or bank/Shariah approval of a posting engine.
 * This class performs no financial posting and never changes deposit status.
 */
@Service
public class BankIslamicDepositExecutionEvidenceService {
    private final JdbcTemplate jdbc;
    private BankApprovalNotificationService notifications;

    public record Submission(String primaryEvidenceReference, String secondaryEvidenceReference,
                             String tertiaryEvidenceReference, String accountingPolicyReference,
                             String ledgerMappingReference, String notes) {}
    public record Decision(String decision, String independentReviewReference, String remarks) {}

    public BankIslamicDepositExecutionEvidenceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Autowired
    public void setNotificationService(BankApprovalNotificationService service) { this.notifications = service; }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long fdId) {
        requireId(fdId, "Deposit");
        return jdbc.queryForList("""
            SELECT e.execution_id, e.execution_ref, e.fd_id, e.terms_id, e.contract_typ,
                   e.primary_evidence_ref, e.secondary_evidence_ref, e.tertiary_evidence_ref,
                   e.accounting_policy_ref, e.ledger_mapping_ref, e.notes, e.review_sts,
                   e.crt_by, e.crt_dt, e.submitted_by, e.submitted_at,
                   e.decided_by, e.decided_at, e.review_reference, e.review_remarks
            FROM public.bank_islamic_deposit_execution_review e
            WHERE e.fd_id = ? ORDER BY e.execution_id DESC
            """, fdId);
    }

    @Transactional
    public Map<String, Object> create(Long fdId, Submission input, Authentication actor) {
        requireId(fdId, "Deposit");
        if (input == null) throw badRequest("Execution evidence is required");
        String maker = username(actor);
        // Revalidate live approval and DRAFT state under deposit row lock.
        // A profit proposal or a mere Islamic contract profile is insufficient.
        List<Map<String, Object>> approved = jdbc.queryForList("""
            SELECT fd.fd_id, fd.fd_sts, t.terms_id, t.contract_typ
            FROM public.bank_fixed_deposit fd
            JOIN public.bank_islamic_deposit_contract_snapshot snap ON snap.fd_id = fd.fd_id
            JOIN public.bank_islamic_deposit_profit_terms t
              ON t.fd_id = fd.fd_id AND t.profile_id = snap.profile_id
             AND t.contract_typ = snap.contract_typ AND t.profit_model = snap.profit_model
            WHERE fd.fd_id = ? AND t.terms_sts = 'APPROVED'
            ORDER BY t.terms_id DESC LIMIT 1 FOR UPDATE OF fd
            """, fdId);
        if (approved.isEmpty()) throw conflict("Approved profit terms and Islamic contract snapshot are required");
        Map<String, Object> terms = approved.get(0);
        if (!"DRAFT".equals(terms.get("fd_sts"))) throw conflict("Deposit must remain DRAFT");
        String contract = Objects.toString(terms.get("contract_typ"), "");
        String primary = required(input.primaryEvidenceReference(), "Primary execution evidence", 4, 160);
        String secondary = required(input.secondaryEvidenceReference(), "Secondary execution evidence", 4, 160);
        String third = optional(input.tertiaryEvidenceReference(), "Third execution evidence", 160);
        if ("TAWARRUQ".equals(contract)) {
            if (third == null) throw badRequest("Tawarruq requires commodity purchase, bank sale and onward-sale evidence references");
        } else if ("WAKALAH".equals(contract) || "MUDARABAH".equals(contract)) {
            if (third != null) throw badRequest("Third evidence is not applicable to this contract");
        } else {
            throw conflict("Unsupported Islamic deposit execution contract: " + contract);
        }
        if (primary.equalsIgnoreCase(secondary) ||
                (third != null && (third.equalsIgnoreCase(primary) || third.equalsIgnoreCase(secondary)))) {
            throw badRequest("Each execution evidence type requires a distinct document reference");
        }
        String policy = required(input.accountingPolicyReference(), "Approved accounting policy reference", 4, 160);
        String mapping = required(input.ledgerMappingReference(), "Islamic ledger mapping reference", 4, 160);
        String notes = required(input.notes(), "Execution evidence and accounting assumptions", 20, 1000);
        // Independent business-facing reference: no primary key exposed to users.
        String reference = BusinessReferenceGenerator.next(
                jdbc, BusinessReferenceGenerator.Stream.ISLAMIC_EXECUTION_EVIDENCE);
        Long id = jdbc.queryForObject("""
            INSERT INTO public.bank_islamic_deposit_execution_review
            (execution_ref, fd_id, terms_id, contract_typ, primary_evidence_ref,
             secondary_evidence_ref, tertiary_evidence_ref, accounting_policy_ref,
             ledger_mapping_ref, notes, crt_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING execution_id
            """, Long.class, reference, fdId, terms.get("terms_id"), contract,
                primary, secondary, third, policy, mapping, notes, maker);
        return get(id);
    }

    @Transactional
    public Map<String, Object> submit(Long id, Authentication actor) {
        String maker = username(actor);
        Map<String, Object> row = lock(id);
        if (!"DRAFT".equals(row.get("review_sts"))) throw conflict("Only DRAFT evidence can be submitted");
        if (!"DRAFT".equals(row.get("fd_sts"))) throw conflict("Deposit must remain DRAFT");
        if (!maker.equalsIgnoreCase(Objects.toString(row.get("crt_by"), "")))
            throw conflict("Only the original maker may submit execution evidence");
        if (jdbc.update("""
            UPDATE public.bank_islamic_deposit_execution_review
            SET review_sts = 'SUBMITTED', submitted_by = ?, submitted_at = CURRENT_TIMESTAMP
            WHERE execution_id = ? AND review_sts = 'DRAFT'
            """, maker, id) != 1) throw conflict("Evidence submission was not applied");
        if (notifications != null) notifications.notifyRole("ADMIN", maker, "FIXED_DEPOSITS", "FD_ISLAMIC_EXECUTION",
                id, "PENDING", "Islamic deposit execution evidence requires review",
                Objects.toString(row.get("execution_ref")) + " is awaiting independent evidence review. No posting is authorized.");
        return get(id);
    }

    @Transactional
    public Map<String, Object> decide(Long id, Decision input, Authentication actor) {
        String checker = username(actor);
        if (input == null) throw badRequest("A review decision is required");
        String decision = Objects.toString(input.decision(), "").trim().toUpperCase(Locale.ROOT);
        if (!"ACCEPT".equals(decision) && !"REJECT".equals(decision))
            throw badRequest("Decision must be ACCEPT or REJECT");
        Map<String, Object> row = lock(id);
        if (!"SUBMITTED".equals(row.get("review_sts"))) throw conflict("Only submitted evidence may be reviewed");
        if (!"DRAFT".equals(row.get("fd_sts"))) throw conflict("Deposit must remain DRAFT");
        if (checker.equalsIgnoreCase(Objects.toString(row.get("crt_by"), "")))
            throw conflict("Maker cannot review their own execution evidence");
        String reference = optional(input.independentReviewReference(), "Independent review reference", 160);
        String remarks = optional(input.remarks(), "Review remarks", 1000);
        if ("ACCEPT".equals(decision) && (reference == null || reference.length() < 4))
            throw badRequest("An external evidence review reference is required");
        if ("REJECT".equals(decision) && (remarks == null || remarks.length() < 5))
            throw badRequest("A rejection reason of at least five characters is required");
        String status = "ACCEPT".equals(decision) ? "REVIEWED" : "REJECTED";
        if (jdbc.update("""
            UPDATE public.bank_islamic_deposit_execution_review
            SET review_sts = ?, decided_by = ?, decided_at = CURRENT_TIMESTAMP,
                review_reference = ?, review_remarks = ?
            WHERE execution_id = ? AND review_sts = 'SUBMITTED'
            """, status, checker, "REVIEWED".equals(status) ? reference : null, remarks, id) != 1)
            throw conflict("Evidence decision was not applied");
        if (notifications != null) notifications.notifyUser(Objects.toString(row.get("crt_by"), ""),
                "FIXED_DEPOSITS", "FD_ISLAMIC_EXECUTION", id, status,
                "Islamic execution evidence " + status.toLowerCase(Locale.ROOT),
                "Evidence review completed. Islamic contract execution and ledger posting remain disabled.");
        return get(id);
    }

    private Map<String, Object> get(Long id) {
        List<Map<String, Object>> result = byId(id, false);
        if (result.isEmpty()) throw notFound("Evidence record not found");
        return result.get(0);
    }
    private Map<String, Object> lock(Long id) {
        List<Map<String, Object>> result = byId(id, true);
        if (result.isEmpty()) throw notFound("Evidence record not found");
        return result.get(0);
    }
    private List<Map<String, Object>> byId(Long id, boolean lock) {
        requireId(id, "Evidence");
        return jdbc.queryForList("""
            SELECT e.execution_id, e.execution_ref, e.fd_id, e.review_sts, e.crt_by, fd.fd_sts
            FROM public.bank_islamic_deposit_execution_review e
            JOIN public.bank_fixed_deposit fd ON fd.fd_id = e.fd_id
            WHERE e.execution_id = ?
            """ + (lock ? " FOR UPDATE OF e, fd" : ""), id);
    }
    private String required(String value, String label, int min, int max) {
        String trimmed = Objects.toString(value, "").trim();
        if (trimmed.length() < min || trimmed.length() > max)
            throw badRequest(label + " must contain " + min + " to " + max + " characters");
        return trimmed;
    }
    private String optional(String value, String label, int max) {
        String trimmed = Objects.toString(value, "").trim();
        if (trimmed.length() > max) throw badRequest(label + " is too long");
        return trimmed.isEmpty() ? null : trimmed;
    }
    private void requireId(Long id, String label) {
        if (id == null || id <= 0) throw badRequest(label + " ID is required");
    }
    private String username(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || auth.getName().isBlank() ||
                "anonymousUser".equals(auth.getName()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        return auth.getName();
    }
    private ResponseStatusException badRequest(String msg) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg); }
    private ResponseStatusException conflict(String msg) { return new ResponseStatusException(HttpStatus.CONFLICT, msg); }
    private ResponseStatusException notFound(String msg) { return new ResponseStatusException(HttpStatus.NOT_FOUND, msg); }
}
