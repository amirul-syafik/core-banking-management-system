package com.cbms.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Islamic fixed/term-deposit DRAFT preparation only. In particular, neither
 * profile approval nor snapshot creation authorizes settlement or accounting.
 */
@Service
public class BankIslamicDepositDraftService {
    private final JdbcTemplate jdbc;

    public record Readiness(Long schemeId, boolean canCreateDraft, Long approvedProfileId,
                            String contractReference, String contractType, String profitModel,
                            String shariahApprovalReference, String message) {}

    public BankIslamicDepositDraftService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Readiness readiness(Long schemeId) {
        if (schemeId == null || schemeId <= 0) throw badRequest("Scheme is required");
        List<Map<String, Object>> schemes = jdbc.queryForList("""
                SELECT s.schm_id, s.schm_sts, p.prod_id, p.prod_cat, p.prod_sts, p.banking_typ
                FROM public.bank_scheme s
                JOIN public.bank_product p ON p.prod_id = s.prod_id
                WHERE s.schm_id = ?
                """, schemeId);
        if (schemes.isEmpty()) throw notFound("Scheme not found");
        Map<String, Object> scheme = schemes.get(0);
        if (!"ISLAMIC".equals(scheme.get("banking_typ")) ||
                !("FIXED_DEPOSIT".equals(scheme.get("prod_cat")) ||
                  "TERM_DEPOSIT".equals(scheme.get("prod_cat")))) {
            throw badRequest("This endpoint is for Islamic fixed or term deposit schemes only");
        }
        if (!"ACTIVE".equals(scheme.get("schm_sts")) || !"ACTIVE".equals(scheme.get("prod_sts"))) {
            return new Readiness(schemeId, false, null, null, null, null, null,
                    "Scheme and product must both be active");
        }
        List<Map<String, Object>> profiles = approvedProfiles(schemeId, ((Number) scheme.get("prod_id")).longValue(), false);
        if (profiles.isEmpty()) {
            return new Readiness(schemeId, false, null, null, null, null, null,
                    "An approved Islamic contract profile is required before creating a deposit draft");
        }
        Map<String, Object> profile = profiles.get(0);
        if (profile.get("shariah_approval_ref") == null ||
                String.valueOf(profile.get("shariah_approval_ref")).isBlank() ||
                profile.get("decided_by") == null || profile.get("decided_at") == null) {
            return new Readiness(schemeId, false, null, null, null, null, null,
                    "Approved Islamic contract review details are incomplete");
        }
        return new Readiness(schemeId, true, ((Number) profile.get("profile_id")).longValue(),
                (String) profile.get("contract_ref"), (String) profile.get("contract_typ"), (String) profile.get("profit_model"),
                (String) profile.get("shariah_approval_ref"),
                "Draft creation only. Placement, profit calculation and posting remain unavailable");
    }

    /** Called inside the same transaction as FD creation, before any deposit row is inserted. */
    public Map<String, Object> lockApprovedProfile(Long schemeId, Long productId) {
        List<Map<String, Object>> profiles = approvedProfiles(schemeId, productId, true);
        if (profiles.isEmpty()) {
            throw conflict("An approved Islamic contract profile for this scheme and product is required");
        }
        Map<String, Object> profile = profiles.get(0);
        if (profile.get("shariah_approval_ref") == null ||
                String.valueOf(profile.get("shariah_approval_ref")).isBlank() ||
                profile.get("decided_by") == null || profile.get("decided_at") == null) {
            throw conflict("Approved Islamic contract review details are incomplete");
        }
        return profile;
    }

    private List<Map<String, Object>> approvedProfiles(Long schemeId, Long productId, boolean lock) {
        return jdbc.queryForList("""
                SELECT profile_id, contract_ref, schm_id, prod_id, contract_typ, profit_model, terms_summary,
                       shariah_approval_ref, decided_by, decided_at
                FROM public.bank_islamic_contract_profile
                WHERE schm_id = ? AND prod_id = ? AND profile_sts = 'APPROVED'
                ORDER BY profile_id DESC LIMIT 1
                """ + (lock ? " FOR SHARE" : ""), schemeId, productId);
    }

    /** Immutable contract and indication snapshot; does not settle or activate the deposit. */
    public void captureDraft(Long fdId, Long schemeId, Long productId,
                             Map<String, Object> approvedProfile, BigDecimal indicativeRate,
                             String maker) {
        if (fdId == null || fdId <= 0 || approvedProfile == null || maker == null || maker.isBlank()) {
            throw badRequest("Incomplete Islamic deposit draft snapshot");
        }
        if (((Number) approvedProfile.get("schm_id")).longValue() != schemeId ||
                ((Number) approvedProfile.get("prod_id")).longValue() != productId) {
            throw conflict("Contract profile does not match deposit scheme/product");
        }
        int inserted = jdbc.update("""
                INSERT INTO public.bank_islamic_deposit_contract_snapshot
                (fd_id, profile_id, schm_id, prod_id, contract_typ, profit_model, terms_summary,
                 shariah_approval_ref, profile_approved_by, profile_approved_at,
                 indicative_rate_pct, snapshot_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, fdId, approvedProfile.get("profile_id"), schemeId, productId,
                approvedProfile.get("contract_typ"), approvedProfile.get("profit_model"),
                approvedProfile.get("terms_summary"), approvedProfile.get("shariah_approval_ref"),
                approvedProfile.get("decided_by"), approvedProfile.get("decided_at"),
                indicativeRate, maker);
        if (inserted != 1) throw conflict("Islamic deposit contract snapshot could not be saved");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> depositContract(Long fdId) {
        if (fdId == null || fdId <= 0) throw badRequest("Deposit is required");
        List<Map<String, Object>> records = jdbc.queryForList("""
                SELECT snap.fd_id, snap.profile_id, profile.contract_ref, snap.schm_id, snap.prod_id,
                       snap.contract_typ, snap.profit_model, snap.terms_summary,
                       snap.shariah_approval_ref, snap.profile_approved_by, snap.profile_approved_at,
                       snap.indicative_rate_pct, snap.snapshot_by, snap.snapshot_at,
                       fd.fd_sts AS deposit_status
                FROM public.bank_islamic_deposit_contract_snapshot snap
                JOIN public.bank_fixed_deposit fd ON fd.fd_id = snap.fd_id
                JOIN public.bank_islamic_contract_profile profile ON profile.profile_id = snap.profile_id
                WHERE snap.fd_id = ?
                """, fdId);
        if (records.isEmpty()) throw notFound("Islamic contract snapshot not found for deposit");
        return records.get(0);
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
    private ResponseStatusException notFound(String reason) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
    }
}
