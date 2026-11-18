package com.cbms.service;

import com.cbms.reference.BusinessReferenceGenerator;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Maker/checker bank execution-policy documentary register. No policy is trusted by
 * preflight until a SEPARATE bank-governance attestation has been performed outside
 * the CBMS application. Admin approval alone never unlocks execution or posting.
 */
@Service
public class BankIslamicExecutionPolicyService {
    private static final Set<String> TYPES = Set.of("TAWARRUQ", "WAKALAH", "MUDARABAH");
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");
    private final JdbcTemplate jdbc;

    public BankIslamicExecutionPolicyService(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public record Draft(String contractType, String orderedEventCodes, String trustedExternalSystems,
                        String effectiveFrom, String effectiveUntil, String policyBasis,
                        String externalSignoffReference) { }
    public record Decision(String decision, String reviewReference, String remarks) { }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String contractType) {
        String type = type(contractType);
        return jdbc.queryForList("""
                SELECT policy_id, policy_ref, contract_typ, event_codes, trusted_systems,
                       effective_from, effective_until, policy_basis, external_signoff_ref,
                       policy_sts, crt_by, crt_dt, submitted_by, submitted_at,
                       decided_by, decided_at, review_ref, review_remarks,
                       bank_signoff_verified, bank_signoff_verified_at
                FROM public.bank_islamic_execution_policy
                WHERE contract_typ = ? ORDER BY policy_id DESC LIMIT 100
                """, type);
    }

    @Transactional
    public Map<String, Object> create(Draft request, Authentication auth) {
        String user = username(auth);
        if (request == null) throw badRequest("Execution policy is required");
        String type = type(request.contractType());
        String events = encodeTokens(request.orderedEventCodes(), "Event codes", 2, 10);
        String systems = encodeTokens(request.trustedExternalSystems(), "Trusted external systems", 1, 10);
        Instant from = timestamp(request.effectiveFrom(), "Effective from");
        Instant until = request.effectiveUntil() == null || request.effectiveUntil().isBlank() ? null
                : timestamp(request.effectiveUntil(), "Effective until");
        if (until != null && !until.isAfter(from)) throw badRequest("Effective until must follow effective from");
        String basis = required(request.policyBasis(), "Policy basis", 20, 2000);
        String signoffRef = required(request.externalSignoffReference(), "External bank sign-off reference", 4, 160);
        String ref = BusinessReferenceGenerator.next(jdbc, BusinessReferenceGenerator.Stream.ISLAMIC_EXECUTION_POLICY);
        Long id = jdbc.queryForObject("""
                INSERT INTO public.bank_islamic_execution_policy
                (policy_ref, contract_typ, event_codes, trusted_systems, effective_from,
                 effective_until, policy_basis, external_signoff_ref, crt_by)
                VALUES (?,?,?,?,?,?,?,?,?) RETURNING policy_id
                """, Long.class, ref, type, events, systems, OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                until == null ? null : OffsetDateTime.ofInstant(until, ZoneOffset.UTC), basis, signoffRef, user);
        return get(id);
    }

    @Transactional
    public Map<String, Object> submit(Long id, Authentication auth) {
        String user = username(auth);
        Map<String, Object> policy = locked(id);
        if (!"DRAFT".equals(policy.get("policy_sts"))) throw conflict("Only draft policies may be submitted");
        if (!user.equalsIgnoreCase(String.valueOf(policy.get("crt_by"))))
            throw conflict("Only the original maker may submit the policy");
        if (jdbc.update("""
                UPDATE public.bank_islamic_execution_policy
                SET policy_sts='SUBMITTED', submitted_by=?, submitted_at=CURRENT_TIMESTAMP
                WHERE policy_id=? AND policy_sts='DRAFT'
                """, user, id) != 1) throw conflict("Policy has already been submitted");
        return get(id);
    }

    @Transactional
    public Map<String, Object> decide(Long id, Decision decision, Authentication auth) {
        String checker = username(auth);
        if (decision == null) throw badRequest("Review decision is required");
        String action = Objects.toString(decision.decision(), "").trim().toUpperCase(Locale.ROOT);
        if (!"APPROVE".equals(action) && !"REJECT".equals(action))
            throw badRequest("Decision must be APPROVE or REJECT");
        Map<String, Object> policy = locked(id);
        if (!"SUBMITTED".equals(policy.get("policy_sts"))) throw conflict("Only submitted policies may be reviewed");
        if (checker.equalsIgnoreCase(String.valueOf(policy.get("crt_by"))))
            throw conflict("Maker cannot review their own policy");
        String reference = optional(decision.reviewReference(), 160);
        String remarks = optional(decision.remarks(), 1000);
        if ("APPROVE".equals(action) && (reference == null || reference.length() < 4))
            throw badRequest("Independent policy-review reference is required");
        if ("REJECT".equals(action) && (remarks == null || remarks.length() < 5))
            throw badRequest("Rejection reason must have at least five characters");
        String status = "APPROVE".equals(action) ? "APPROVED" : "REJECTED";
        if (jdbc.update("""
                UPDATE public.bank_islamic_execution_policy
                SET policy_sts=?, decided_by=?, decided_at=CURRENT_TIMESTAMP,
                    review_ref=?, review_remarks=?
                WHERE policy_id=? AND policy_sts='SUBMITTED'
                """, status, checker, "APPROVED".equals(status) ? reference : null,
                remarks, id) != 1) throw conflict("Policy decision was not applied");
        // bank_signoff_verified is deliberately NEVER changed here.
        return get(id);
    }

    private Map<String, Object> locked(Long id) {
        requireId(id);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT policy_id, policy_sts, crt_by
                FROM public.bank_islamic_execution_policy WHERE policy_id=? FOR UPDATE
                """, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution policy not found");
        return rows.get(0);
    }

    private Map<String, Object> get(Long id) {
        requireId(id);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT policy_id, policy_ref, contract_typ, event_codes, trusted_systems,
                       effective_from, effective_until, policy_basis, external_signoff_ref,
                       policy_sts, crt_by, crt_dt, submitted_by, submitted_at,
                       decided_by, decided_at, review_ref, review_remarks,
                       bank_signoff_verified, bank_signoff_verified_at
                FROM public.bank_islamic_execution_policy WHERE policy_id=?
                """, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution policy not found");
        return rows.get(0);
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) throw badRequest("Valid policy reference required");
    }

    static String type(String value) {
        String cleaned = Objects.toString(value, "").trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(cleaned)) throw badRequest("Unsupported Islamic contract type");
        return cleaned;
    }

    static String encodeTokens(String value, String label, int min, int max) {
        String source = Objects.toString(value, "").trim().toUpperCase(Locale.ROOT);
        String[] tokens = source.split(",", -1);
        if (tokens.length < min || tokens.length > max)
            throw badRequest(label + " must contain " + min + " to " + max + " entries");
        List<String> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (String token : tokens) {
            String code = token.trim();
            if (!CODE.matcher(code).matches() || !unique.add(code))
                throw badRequest(label + " must contain unique uppercase codes");
            result.add(code);
        }
        return String.join(",", result);
    }

    private static Instant timestamp(String value, String label) {
        try { return OffsetDateTime.parse(Objects.toString(value, "")).toInstant(); }
        catch (RuntimeException ex) { throw badRequest(label + " requires an ISO timestamp with timezone"); }
    }

    private static String username(Authentication auth) {
        String name = auth == null ? null : auth.getName();
        if (name == null || name.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        return name;
    }

    private static String required(String text, String label, int min, int max) {
        String result = optional(text, max);
        if (result == null || result.length() < min) throw badRequest(label + " must be at least " + min + " characters");
        return result;
    }

    private static String optional(String text, int max) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.trim();
        if (trimmed.length() > max) throw badRequest("Field exceeds " + max + " characters");
        return trimmed;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
