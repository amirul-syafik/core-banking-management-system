package com.cbms.service;

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
 * Maker/checker register of proposed Islamic contract profiles. This does not
 * execute commodity trades, calculate contractual profit or post to the GL.
 */
@Service
public class BankIslamicContractProfileService {
    private final JdbcTemplate jdbc;
    private BankApprovalNotificationService notifications;

    @Autowired
    public void setNotificationService(BankApprovalNotificationService notifications) {
        this.notifications = notifications;
    }

    public record DraftRequest(Long schemeId, String contractType, String termsSummary) {}
    public record DecisionRequest(String decision, String shariahApprovalReference, String remarks) {}

    public BankIslamicContractProfileService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long schemeId) {
        if (schemeId == null || schemeId <= 0) {
            throw badRequest("Scheme is required");
        }
        return jdbc.queryForList("""
                SELECT profile_id, contract_ref, schm_id, prod_id, contract_typ, profit_model, terms_summary,
                       profile_sts, shariah_approval_ref, review_remarks,
                       crt_by, crt_dt, submitted_by, submitted_at, decided_by, decided_at
                FROM public.bank_islamic_contract_profile
                WHERE schm_id = ? ORDER BY profile_id DESC
                """, schemeId);
    }

    @Transactional
    public Map<String, Object> create(DraftRequest request, Authentication actor) {
        String maker = username(actor);
        if (request == null || request.schemeId() == null || request.schemeId() <= 0) {
            throw badRequest("Scheme is required");
        }
        List<Map<String, Object>> products = jdbc.queryForList("""
                SELECT p.prod_id, p.prod_cat, p.banking_typ
                FROM public.bank_scheme s
                JOIN public.bank_product p ON p.prod_id = s.prod_id
                WHERE s.schm_id = ? AND s.schm_sts = 'ACTIVE' AND p.prod_sts = 'ACTIVE'
                """, request.schemeId());
        if (products.isEmpty()) throw conflict("Scheme and product must be active");
        Map<String, Object> product = products.get(0);
        if (!"ISLAMIC".equals(product.get("banking_typ"))) {
            throw conflict("An Islamic contract profile can only be added to an Islamic product");
        }
        String category = String.valueOf(product.get("prod_cat"));
        String type = normalize(request.contractType());
        String model;
        if ("FIXED_DEPOSIT".equals(category) || "TERM_DEPOSIT".equals(category)) {
            model = switch (type) {
                case "TAWARRUQ" -> "DEFERRED_SALE";
                case "WAKALAH" -> "AGENCY";
                case "MUDARABAH" -> "PROFIT_SHARING";
                default -> throw badRequest("Unsupported contract for Islamic deposit product");
            };
        } else if ("HIRE_PURCHASE".equals(category) || "PERSONAL_FINANCING".equals(category)
                || "TERM_LOAN".equals(category)) {
            model = switch (type) {
                case "TAWARRUQ" -> "DEFERRED_SALE";
                case "MURABAHAH" -> "COST_PLUS_SALE";
                case "IJARAH" -> "RENTAL";
                default -> throw badRequest("Unsupported contract for Islamic financing product");
            };
        } else {
            throw conflict("Unsupported Islamic product category");
        }
        String terms = request.termsSummary() == null ? "" : request.termsSummary().trim();
        if (terms.length() < 20 || terms.length() > 2000) {
            throw badRequest("Contract terms summary must contain 20 to 2000 characters");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO public.bank_islamic_contract_profile
                (schm_id, prod_id, contract_typ, profit_model, terms_summary, crt_by)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING profile_id
                """, Long.class, request.schemeId(), product.get("prod_id"), type, model, terms, maker);
        return get(id);
    }

    @Transactional
    public Map<String, Object> submit(Long id, Authentication actor) {
        String maker = username(actor);
        Map<String, Object> row = lock(id);
        if (!"DRAFT".equals(row.get("profile_sts"))) throw conflict("Only DRAFT profiles can be submitted");
        if (!maker.equalsIgnoreCase((String) row.get("crt_by"))) {
            throw conflict("Only the profile maker can submit it");
        }
        if (jdbc.update("""
                UPDATE public.bank_islamic_contract_profile
                SET profile_sts = 'SUBMITTED', submitted_by = ?, submitted_at = CURRENT_TIMESTAMP
                WHERE profile_id = ? AND profile_sts = 'DRAFT'
                """, maker, id) != 1) throw conflict("Profile has already been processed");
        if (notifications != null) notifications.notifyRole("ADMIN", maker, "SCHEMES", "ISLAMIC_CONTRACT", id,
                "PENDING", "Islamic contract review required",
                "Contract " + row.get("contract_ref") + " is awaiting independent Shariah-documentation review.");
        return get(id);
    }

    @Transactional
    public Map<String, Object> decide(Long id, DecisionRequest request, Authentication actor) {
        String checker = username(actor);
        if (request == null) throw badRequest("Decision is required");
        String decision = normalize(request.decision());
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            throw badRequest("Decision must be APPROVE or REJECT");
        }
        Map<String, Object> row = lock(id);
        if (!"SUBMITTED".equals(row.get("profile_sts"))) {
            throw conflict("Only SUBMITTED profiles can be decided");
        }
        if (checker.equalsIgnoreCase(Objects.toString(row.get("crt_by"), ""))) {
            throw conflict("Maker may not approve or reject their own Islamic contract profile");
        }
        String reference = request.shariahApprovalReference() == null ? "" : request.shariahApprovalReference().trim();
        String remarks = request.remarks() == null ? "" : request.remarks().trim();
        if (remarks.length() > 1000) throw badRequest("Review remarks are too long");
        if ("APPROVE".equals(decision)) {
            if (reference.length() < 4 || reference.length() > 160) {
                throw badRequest("A documented external Shariah approval reference is required");
            }
            Integer approved = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM public.bank_islamic_contract_profile
                    WHERE schm_id = ? AND profile_sts = 'APPROVED'
                    """, Integer.class, row.get("schm_id"));
            if (approved != null && approved > 0) {
                throw conflict("Scheme already has an approved contract profile; amendment workflow is not yet implemented");
            }
        } else if (remarks.isBlank()) {
            throw badRequest("Reason is required for rejection");
        }
        int updated = jdbc.update("""
                UPDATE public.bank_islamic_contract_profile
                SET profile_sts = ?, shariah_approval_ref = ?, review_remarks = ?,
                    decided_by = ?, decided_at = CURRENT_TIMESTAMP
                WHERE profile_id = ? AND profile_sts = 'SUBMITTED'
                """, "APPROVE".equals(decision) ? "APPROVED" : "REJECTED",
                "APPROVE".equals(decision) ? reference : null,
                remarks.isBlank() ? null : remarks, checker, id);
        if (updated != 1) throw conflict("Profile decision was not applied");
        if (notifications != null) notifications.notifyUser((String) row.get("crt_by"), "SCHEMES",
                "ISLAMIC_CONTRACT", id, "APPROVE".equals(decision) ? "APPROVED" : "REJECTED",
                "Islamic contract profile " + ("APPROVE".equals(decision) ? "approved" : "rejected"),
                "Contract " + row.get("contract_ref") + " was reviewed. Open Scheme Maintenance for the decision.");
        return get(id);
    }

    private Map<String, Object> get(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT profile_id, contract_ref, schm_id, prod_id, contract_typ, profit_model, terms_summary,
                       profile_sts, shariah_approval_ref, review_remarks,
                       crt_by, crt_dt, submitted_by, submitted_at, decided_by, decided_at
                FROM public.bank_islamic_contract_profile WHERE profile_id = ?
                """, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract profile not found");
        return rows.get(0);
    }

    private Map<String, Object> lock(Long id) {
        if (id == null || id <= 0) throw badRequest("Profile ID is required");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT profile_id, contract_ref, schm_id, profile_sts, crt_by
                FROM public.bank_islamic_contract_profile
                WHERE profile_id = ? FOR UPDATE
                """, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract profile not found");
        return rows.get(0);
    }

    private String username(Authentication actor) {
        if (actor == null || !actor.isAuthenticated() || actor.getName() == null || actor.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user required");
        }
        return actor.getName();
    }
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
    private ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
    private ResponseStatusException conflict(String detail) {
        return new ResponseStatusException(HttpStatus.CONFLICT, detail);
    }
}
