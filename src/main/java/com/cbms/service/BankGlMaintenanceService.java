package com.cbms.service;

import com.cbms.controller.BankGlMaintenanceController.ChangeRequest;
import com.cbms.controller.BankGlMaintenanceController.DecisionRequest;
import com.cbms.reference.BusinessReferenceGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Maker-checker maintenance of chart-of-accounts definitions, never journal posting. */
@Service
public class BankGlMaintenanceService {
    private static final int PAGE_SIZE = 10;
    // These account numbers remain referenced directly by older posting/reconciliation services.
    // Until the corresponding event-to-GL mappings are configurable, never deactivate them.
    private static final Set<String> LEGACY_SYSTEM_CODES = Set.of("100100", "200100", "200200", "200300", "500100",
            "HP_PRINCIPAL_RECEIVABLE", "DISBURSEMENT_CLEARING");
    private final JdbcTemplate jdbc;
    public BankGlMaintenanceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
    private static String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in is required");
        return authentication.getName();
    }
    private static int checkedPage(int page) {
        if (page < 0 || page > 100000) throw bad("Invalid page");
        return page;
    }
    private static String mandatory(String input, String label, int min, int max) {
        String value = input == null ? "" : input.trim();
        if (value.length() < min || value.length() > max) throw bad(label + " is required (" + min + "–" + max + " characters)");
        return value;
    }
    private static String code(String value) {
        String result = mandatory(value, "GL code", 1, 30).toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z0-9_-]+")) throw bad("Invalid GL code");
        return result;
    }
    private static String currency(String value) {
        String result = mandatory(value, "Currency", 3, 3).toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z]{3}")) throw bad("Currency must use a three-letter code");
        return result;
    }
    private static String action(String value) {
        String action = mandatory(value, "Action", 1, 15).toUpperCase(Locale.ROOT);
        if (!Set.of("CREATE", "RENAME", "ACTIVATE", "DEACTIVATE").contains(action)) throw bad("Invalid GL maintenance action");
        return action;
    }
    private static String glType(String value) {
        String type = mandatory(value, "GL type", 1, 20).toUpperCase(Locale.ROOT);
        if (!Set.of("ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE").contains(type))
            throw bad("Invalid GL account type");
        return type;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> accounts(String query, int page) {
        checkedPage(page);
        String term = query == null ? "" : query.trim();
        if (term.length() > 80) throw bad("Search term is too long");
        String escaped = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        String where = " FROM public.bank_ledger_account WHERE (gl_cd ILIKE ? ESCAPE '!' OR gl_nm ILIKE ? ESCAPE '!')";
        Long count = jdbc.queryForObject("SELECT COUNT(*) " + where, Long.class, escaped, escaped);
        long total = count == null ? 0 : count;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT gl_cd, gl_nm, gl_typ, ccy_cd, act_flg " + where + " ORDER BY gl_cd LIMIT ? OFFSET ?",
                escaped, escaped, PAGE_SIZE, page * PAGE_SIZE);
        return Map.of("content", rows, "totalElements", total, "page", page,
                "totalPages", Math.max(1L, (total + PAGE_SIZE - 1) / PAGE_SIZE));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> requests(String status, int page) {
        checkedPage(page);
        String filter = status == null ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "SUBMITTED", "APPROVED", "REJECTED").contains(filter)) throw bad("Invalid request status");
        String where = filter.equals("ALL") ? "" : " WHERE request_sts = ?";
        Object[] countArgs = filter.equals("ALL") ? new Object[]{} : new Object[]{filter};
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM public.bank_gl_account_change_request" + where,
                Long.class, countArgs);
        List<Map<String, Object>> rows = filter.equals("ALL")
                ? jdbc.queryForList("SELECT * FROM public.bank_gl_account_change_request ORDER BY request_id DESC LIMIT ? OFFSET ?",
                    PAGE_SIZE, page * PAGE_SIZE)
                : jdbc.queryForList("SELECT * FROM public.bank_gl_account_change_request" + where +
                    " ORDER BY request_id DESC LIMIT ? OFFSET ?", filter, PAGE_SIZE, page * PAGE_SIZE);
        long total = count == null ? 0 : count;
        return Map.of("content", rows, "totalElements", total, "page", page,
                "totalPages", Math.max(1L, (total + PAGE_SIZE - 1) / PAGE_SIZE));
    }

    private Map<String, Object> existingAccount(String glCode, boolean forUpdate) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT gl_cd, gl_nm, gl_typ, ccy_cd, act_flg " +
                "FROM public.bank_ledger_account WHERE gl_cd = ?" + (forUpdate ? " FOR UPDATE" : ""), glCode);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional
    public Map<String, Object> propose(ChangeRequest input, Authentication authentication) {
        if (input == null) throw bad("Request is required");
        String maker = actor(authentication);
        String action = action(input.action());
        String code = code(input.glCode());
        String name = mandatory(input.glName(), "GL name", 2, 150);
        String type = glType(input.glType());
        String currency = currency(input.currency());
        String reason = mandatory(input.reason(), "Reason", 5, 500);
        Map<String, Object> account = existingAccount(code, true);
        if (action.equals("CREATE") && account != null) throw conflict("GL code already exists");
        if (!action.equals("CREATE") && account == null) throw bad("Select an existing GL account");
        String oldName = null;
        Boolean oldActive = null;
        if (account != null) {
            oldName = String.valueOf(account.get("gl_nm"));
            oldActive = Boolean.TRUE.equals(account.get("act_flg"));
            if (!type.equals(account.get("gl_typ")) || !currency.equals(account.get("ccy_cd")))
                throw bad("GL type and currency cannot be changed");
            if (action.equals("RENAME") && name.equals(oldName)) throw conflict("No changes to save");
            if (!action.equals("RENAME") && !name.equals(oldName))
                throw bad("Account name may only change in a Rename request");
            if (action.equals("ACTIVATE") && oldActive) throw conflict("GL account is already active");
            if (action.equals("DEACTIVATE") && !oldActive) throw conflict("GL account is already inactive");
            if (action.equals("DEACTIVATE")) assertMayDeactivate(code);
        }
        String requestRef = BusinessReferenceGenerator.next(jdbc, BusinessReferenceGenerator.Stream.GL_CHANGE_REQUEST);
        try {
            jdbc.update("""
                INSERT INTO public.bank_gl_account_change_request
                  (request_ref, action_typ, gl_cd, gl_nm, gl_typ, ccy_cd, old_gl_nm,
                   old_act_flg, request_reason, requested_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, requestRef, action, code, name, type, currency, oldName, oldActive, reason, maker);
        } catch (DataIntegrityViolationException ex) {
            throw conflict("A GL change request is already pending for this code, or a constraint was violated");
        }
        return Map.of("reference", requestRef, "status", "SUBMITTED");
    }

    private void assertMayDeactivate(String code) {
        if (LEGACY_SYSTEM_CODES.contains(code)) throw conflict("This GL is required by existing banking posting configuration");
        Long used = jdbc.queryForObject("""
              SELECT COUNT(*) FROM public.bank_ledger_entry e
              JOIN public.bank_ledger_account a ON a.gl_id = e.gl_id
              WHERE a.gl_cd = ?
              """, Long.class, code);
        if (used != null && used > 0) throw conflict("This GL has ledger entries and cannot be deactivated");
        // Proposed Islamic journal mapping can also rely on a code before any posting occurs.
        Long proposed = jdbc.queryForObject("""
              SELECT COUNT(*) FROM public.bank_islamic_deposit_ledger_mapping_review m
              WHERE m.mapping_sts IN ('DRAFT','SUBMITTED','REVIEWED')
                AND ? IN (m.placement_debit_gl, m.placement_credit_gl,
                  m.accrual_debit_gl, m.accrual_credit_gl,
                  m.payout_debit_gl, m.payout_credit_gl,
                  m.maturity_debit_gl, m.maturity_credit_gl)
              """, Long.class, code);
        if (proposed != null && proposed > 0) throw conflict("This GL is referenced by an Islamic deposit mapping");
    }

    @Transactional
    public Map<String, Object> decide(long id, DecisionRequest decision, Authentication authentication) {
        String checker = actor(authentication);
        if (id < 1 || decision == null || decision.approve() == null) throw bad("Decision is required");
        String remarks = decision.remarks() == null ? "" : decision.remarks().trim();
        if (!decision.approve() && remarks.length() < 5) throw bad("Rejection reason is required");
        if (remarks.length() > 500) throw bad("Review remarks are too long");
        Map<String, Object> request;
        try {
            request = jdbc.queryForMap("SELECT * FROM public.bank_gl_account_change_request WHERE request_id = ? FOR UPDATE", id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "GL maintenance request not found");
        }
        if (!"SUBMITTED".equals(request.get("request_sts"))) throw conflict("Request already decided");
        if (checker.equalsIgnoreCase(String.valueOf(request.get("requested_by"))))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Maker cannot approve own GL change");
        if (decision.approve()) applyChange(request);
        int updated = jdbc.update("""
             UPDATE public.bank_gl_account_change_request
             SET request_sts = ?, decided_by = ?, decided_at = CURRENT_TIMESTAMP, decision_remarks = ?
             WHERE request_id = ? AND request_sts = 'SUBMITTED'
             """, decision.approve() ? "APPROVED" : "REJECTED", checker, remarks.isBlank() ? null : remarks, id);
        if (updated != 1) throw conflict("Request is no longer pending");
        return Map.of("reference", String.valueOf(request.get("request_ref")),
                "status", decision.approve() ? "APPROVED" : "REJECTED");
    }

    private void applyChange(Map<String, Object> request) {
        String code = String.valueOf(request.get("gl_cd"));
        String action = String.valueOf(request.get("action_typ"));
        if (action.equals("CREATE")) {
            if (existingAccount(code, false) != null) throw conflict("GL code already exists");
            try {
                // Newly approved GLs remain inactive; activation is a distinct reviewed action.
                jdbc.update("""
                    INSERT INTO public.bank_ledger_account (gl_cd, gl_nm, gl_typ, ccy_cd, act_flg)
                    VALUES (?, ?, ?, ?, FALSE)
                    """, code, request.get("gl_nm"), request.get("gl_typ"), request.get("ccy_cd"));
            } catch (DataIntegrityViolationException ex) {
                throw conflict("GL code already exists");
            }
            return;
        }
        Map<String, Object> existing = existingAccount(code, true);
        if (existing == null || !existing.get("gl_nm").equals(request.get("old_gl_nm")) ||
                !existing.get("act_flg").equals(request.get("old_act_flg")) ||
                !existing.get("gl_typ").equals(request.get("gl_typ")) ||
                !existing.get("ccy_cd").equals(request.get("ccy_cd")))
            throw conflict("GL account changed since the request was submitted; reject and create a new request");
        switch (action) {
            case "RENAME" -> jdbc.update("UPDATE public.bank_ledger_account SET gl_nm = ? WHERE gl_cd = ?",
                    request.get("gl_nm"), code);
            case "ACTIVATE" -> jdbc.update("UPDATE public.bank_ledger_account SET act_flg = TRUE WHERE gl_cd = ?", code);
            case "DEACTIVATE" -> {
                assertMayDeactivate(code);
                jdbc.update("UPDATE public.bank_ledger_account SET act_flg = FALSE WHERE gl_cd = ?", code);
            }
            default -> throw bad("Invalid GL change action");
        }
    }
}
