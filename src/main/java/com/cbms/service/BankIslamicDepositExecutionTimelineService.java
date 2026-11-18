package com.cbms.service;

import com.cbms.reference.BusinessReferenceGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Contract-specific chronology of externally asserted documentary events.
 * REVIEWED means that an independent user reviewed chronology/document references.
 * It NEVER means commodity trades/investment settlement were verified by an external
 * counterparty, and no deposit state, balances, GL entries or payments are changed.
 */
@Service
public class BankIslamicDepositExecutionTimelineService {
    private final JdbcTemplate jdbc;
    private BankApprovalNotificationService notifications;

    public record Proposal(String primaryRecordedAt, String secondaryRecordedAt,
                           String tertiaryRecordedAt, String controlNotes) {}
    public record Decision(String decision, String reviewReference, String remarks) {}

    public BankIslamicDepositExecutionTimelineService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Autowired
    public void setNotifications(BankApprovalNotificationService notifications) { this.notifications = notifications; }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long fdId) {
        requireId(fdId);
        return jdbc.queryForList("""
            SELECT t.timeline_id, t.timeline_ref, t.fd_id, t.execution_id, t.contract_typ,
                   t.primary_recorded_at, t.secondary_recorded_at, t.tertiary_recorded_at,
                   e.primary_evidence_ref, e.secondary_evidence_ref, e.tertiary_evidence_ref,
                   t.control_notes, t.review_sts, t.crt_by, t.crt_dt, t.submitted_by,
                   t.submitted_at, t.decided_by, t.decided_at, t.review_reference, t.review_remarks
            FROM public.bank_islamic_deposit_execution_timeline t
            JOIN public.bank_islamic_deposit_execution_review e ON e.execution_id = t.execution_id
            WHERE t.fd_id = ? ORDER BY t.timeline_id DESC
            """, fdId);
    }

    @Transactional
    public Map<String, Object> create(Long fdId, Proposal input, Authentication auth) {
        requireId(fdId);
        if (input == null) throw badRequest("Execution timeline is required");
        String maker = username(auth);
        // Lock deposit before reading the source evidence, preventing concurrent lifecycle changes.
        List<Map<String, Object>> deposits = jdbc.queryForList("""
            SELECT fd.fd_id, fd.fd_sts, snap.contract_typ
            FROM public.bank_fixed_deposit fd
            JOIN public.bank_islamic_deposit_contract_snapshot snap ON snap.fd_id = fd.fd_id
            WHERE fd.fd_id = ? FOR UPDATE OF fd
            """, fdId);
        if (deposits.isEmpty()) throw conflict("An Islamic deposit with an approved contract snapshot is required");
        if (!"DRAFT".equals(deposits.get(0).get("fd_sts"))) throw conflict("Deposit must remain DRAFT");
        String contract = Objects.toString(deposits.get(0).get("contract_typ"), "");
        List<Map<String, Object>> evidence = jdbc.queryForList("""
            SELECT e.execution_id FROM public.bank_islamic_deposit_execution_review e
            WHERE e.fd_id = ? AND e.contract_typ = ? AND e.review_sts = 'REVIEWED'
            ORDER BY e.execution_id DESC LIMIT 1
            """, fdId, contract);
        if (evidence.isEmpty()) throw conflict("Independently reviewed execution evidence is required first");
        if (!List.of("TAWARRUQ", "WAKALAH", "MUDARABAH").contains(contract))
            throw conflict("Unsupported Islamic contract type");
        OffsetDateTime first = timestamp(input.primaryRecordedAt(), "First event");
        OffsetDateTime second = timestamp(input.secondaryRecordedAt(), "Second event");
        if (!first.toInstant().isBefore(second.toInstant()))
            throw badRequest("Second event must be later than first event");
        OffsetDateTime third = null;
        if ("TAWARRUQ".equals(contract)) {
            third = timestamp(input.tertiaryRecordedAt(), "Third event");
            if (!second.toInstant().isBefore(third.toInstant()))
                throw badRequest("Tawarruq onward sale must be later than bank-to-customer sale");
        } else if (input.tertiaryRecordedAt() != null && !input.tertiaryRecordedAt().isBlank()) {
            throw badRequest("Third event is not applicable to this contract");
        }
        if (first.toInstant().isAfter(java.time.Instant.now().plusSeconds(300)) ||
                second.toInstant().isAfter(java.time.Instant.now().plusSeconds(300)) ||
                (third != null && third.toInstant().isAfter(java.time.Instant.now().plusSeconds(300))))
            throw badRequest("External documentary events cannot be future-dated");
        String notes = required(input.controlNotes(), "Documentary sequencing review notes", 20, 1000);
        String ref = BusinessReferenceGenerator.next(jdbc, BusinessReferenceGenerator.Stream.ISLAMIC_EXECUTION_TIMELINE);
        Long id = jdbc.queryForObject("""
            INSERT INTO public.bank_islamic_deposit_execution_timeline
              (timeline_ref, fd_id, execution_id, contract_typ, primary_recorded_at,
               secondary_recorded_at, tertiary_recorded_at, control_notes, crt_by)
            VALUES (?,?,?,?,?,?,?,?,?) RETURNING timeline_id
            """, Long.class, ref, fdId, evidence.get(0).get("execution_id"), contract,
                first, second, third, notes, maker);
        return byId(id, false);
    }

    @Transactional
    public Map<String, Object> submit(Long id, Authentication auth) {
        String maker = username(auth);
        Map<String, Object> row = byId(id, true);
        if (!"DRAFT".equals(row.get("review_sts"))) throw conflict("Only DRAFT timelines can be submitted");
        if (!"DRAFT".equals(row.get("fd_sts"))) throw conflict("Deposit must remain DRAFT");
        if (!maker.equalsIgnoreCase(Objects.toString(row.get("crt_by"), "")))
            throw conflict("Only the original maker may submit the timeline");
        requireSourceEvidence(row);
        int applied = jdbc.update("""
            UPDATE public.bank_islamic_deposit_execution_timeline
            SET review_sts = 'SUBMITTED', submitted_by = ?, submitted_at = CURRENT_TIMESTAMP
            WHERE timeline_id = ? AND review_sts = 'DRAFT'
            """, maker, id);
        if (applied != 1) throw conflict("Timeline submission was not applied");
        if (notifications != null) notifications.notifyRole("ADMIN", maker, "FIXED_DEPOSITS",
                "FD_ISLAMIC_TIMELINE", id, "PENDING", "Islamic execution timeline requires review",
                "Documentary event chronology requires independent review; no contract execution or posting is authorized.");
        return byId(id, false);
    }

    @Transactional
    public Map<String, Object> decide(Long id, Decision input, Authentication auth) {
        String checker = username(auth);
        if (input == null) throw badRequest("A review decision is required");
        String choice = Objects.toString(input.decision(), "").trim().toUpperCase(Locale.ROOT);
        if (!"ACCEPT".equals(choice) && !"REJECT".equals(choice))
            throw badRequest("Decision must be ACCEPT or REJECT");
        Map<String, Object> row = byId(id, true);
        if (!"SUBMITTED".equals(row.get("review_sts"))) throw conflict("Only submitted timelines may be reviewed");
        if (!"DRAFT".equals(row.get("fd_sts"))) throw conflict("Deposit must remain DRAFT");
        if (checker.equalsIgnoreCase(Objects.toString(row.get("crt_by"), "")))
            throw conflict("Maker cannot review their own timeline");
        requireSourceEvidence(row);
        String reference = optional(input.reviewReference(), 160);
        String remarks = optional(input.remarks(), 1000);
        if ("ACCEPT".equals(choice) && (reference == null || reference.length() < 4))
            throw badRequest("Independent document-review reference is required");
        if ("REJECT".equals(choice) && (remarks == null || remarks.length() < 5))
            throw badRequest("Rejection reason must have at least 5 characters");
        String status = "ACCEPT".equals(choice) ? "DOCUMENT_REVIEWED" : "REJECTED";
        if (jdbc.update("""
            UPDATE public.bank_islamic_deposit_execution_timeline
            SET review_sts = ?, decided_by = ?, decided_at = CURRENT_TIMESTAMP,
                review_reference = ?, review_remarks = ?
            WHERE timeline_id = ? AND review_sts = 'SUBMITTED'
            """, status, checker, "DOCUMENT_REVIEWED".equals(status) ? reference : null, remarks, id) != 1)
            throw conflict("Timeline decision was not applied");
        if (notifications != null) notifications.notifyUser(Objects.toString(row.get("crt_by"), ""),
                "FIXED_DEPOSITS", "FD_ISLAMIC_TIMELINE", id, status,
                "Islamic timeline " + status.toLowerCase(Locale.ROOT).replace('_',' '),
                "Documentary chronology decision completed; real execution and posting remain disabled.");
        return byId(id, false);
    }

    private Map<String, Object> byId(Long id, boolean lock) {
        requireId(id);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT t.*, fd.fd_sts, e.review_sts AS evidence_sts
            FROM public.bank_islamic_deposit_execution_timeline t
            JOIN public.bank_fixed_deposit fd ON fd.fd_id = t.fd_id
            JOIN public.bank_islamic_deposit_execution_review e ON e.execution_id = t.execution_id
            WHERE t.timeline_id = ?
            """ + (lock ? " FOR UPDATE OF t, fd" : ""), id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Timeline not found");
        return rows.get(0);
    }
    private void requireSourceEvidence(Map<String, Object> row) {
        if (!"REVIEWED".equals(row.get("evidence_sts")))
            throw conflict("Linked documentary execution evidence is no longer reviewed");
    }
    private OffsetDateTime timestamp(String value, String label) {
        try {
            if (value == null || value.isBlank()) throw new IllegalArgumentException();
            return OffsetDateTime.parse(value.trim());
        } catch (RuntimeException ex) {
            throw badRequest(label + " must include a date, time and timezone offset");
        }
    }
    private String required(String value, String label, int min, int max) {
        String result = Objects.toString(value, "").trim();
        if (result.length() < min || result.length() > max)
            throw badRequest(label + " must contain " + min + " to " + max + " characters");
        return result;
    }
    private String optional(String value, int max) {
        String result = Objects.toString(value, "").trim();
        if (result.length() > max) throw badRequest("Review value is too long");
        return result.isEmpty() ? null : result;
    }
    private void requireId(Long id) {
        if (id == null || id <= 0) throw badRequest("A valid record reference is required");
    }
    private String username(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || auth.getName().isBlank()
                || "anonymousUser".equals(auth.getName()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        return auth.getName();
    }
    private ResponseStatusException badRequest(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
    private ResponseStatusException conflict(String reason) { return new ResponseStatusException(HttpStatus.CONFLICT, reason); }
}
