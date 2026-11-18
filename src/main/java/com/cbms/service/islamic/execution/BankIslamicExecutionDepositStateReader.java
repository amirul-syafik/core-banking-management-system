package com.cbms.service.islamic.execution;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.cbms.service.islamic.execution.IslamicExecutionEvidence.ContractType;
import com.cbms.service.islamic.execution.IslamicExecutionEvidence.DepositSnapshot;
import com.cbms.service.islamic.execution.IslamicExecutionPorts.DepositStateReader;

/**
 * Server-side projection of the existing Islamic deposit lifecycle and review tables.
 * A reviewed document/timeline is NOT evidence that external settlement took place.
 * No request-supplied boolean or document reference can set these flags.
 */
@Repository
public class BankIslamicExecutionDepositStateReader implements DepositStateReader {
    private final JdbcTemplate jdbc;

    public BankIslamicExecutionDepositStateReader(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DepositSnapshot> findDeposit(long depositId) {
        if (depositId <= 0) return Optional.empty();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT fd.fd_id, fd.fd_ref, fd.fd_sts, snap.contract_typ,
                   COALESCE(NULLIF(TRIM(snap.shariah_approval_ref), ''), NULL) IS NOT NULL
                     AND COALESCE(NULLIF(TRIM(snap.profile_approved_by), ''), NULL) IS NOT NULL
                       AS contract_approved,
                   EXISTS (
                     SELECT 1 FROM public.bank_islamic_deposit_profit_terms terms
                     WHERE terms.fd_id = fd.fd_id AND terms.profile_id = snap.profile_id
                       AND terms.contract_typ = snap.contract_typ
                       AND terms.profit_model = snap.profit_model AND terms.terms_sts = 'APPROVED'
                   ) AS profit_approved,
                   EXISTS (
                     SELECT 1 FROM public.bank_islamic_deposit_execution_review evidence
                     JOIN public.bank_islamic_deposit_profit_terms terms
                       ON terms.terms_id = evidence.terms_id
                     WHERE evidence.fd_id = fd.fd_id AND evidence.contract_typ = snap.contract_typ
                       AND evidence.review_sts = 'REVIEWED'
                       AND terms.fd_id = fd.fd_id AND terms.profile_id = snap.profile_id
                       AND terms.contract_typ = snap.contract_typ
                       AND terms.profit_model = snap.profit_model AND terms.terms_sts = 'APPROVED'
                   ) AS evidence_reviewed,
                   EXISTS (
                     SELECT 1 FROM public.bank_islamic_deposit_execution_timeline timeline
                     JOIN public.bank_islamic_deposit_execution_review evidence
                       ON evidence.execution_id = timeline.execution_id
                     JOIN public.bank_islamic_deposit_profit_terms terms
                       ON terms.terms_id = evidence.terms_id
                     WHERE timeline.fd_id = fd.fd_id AND timeline.contract_typ = snap.contract_typ
                       AND timeline.review_sts = 'DOCUMENT_REVIEWED'
                       AND evidence.fd_id = fd.fd_id AND evidence.contract_typ = snap.contract_typ
                       AND evidence.review_sts = 'REVIEWED'
                       AND terms.fd_id = fd.fd_id AND terms.profile_id = snap.profile_id
                       AND terms.contract_typ = snap.contract_typ
                       AND terms.profit_model = snap.profit_model AND terms.terms_sts = 'APPROVED'
                   ) AS timeline_reviewed
            FROM public.bank_fixed_deposit fd
            JOIN public.bank_islamic_deposit_contract_snapshot snap ON snap.fd_id = fd.fd_id
            WHERE fd.fd_id = ?
            """, depositId);
        if (rows.size() != 1) return Optional.empty();
        Map<String, Object> row = rows.get(0);
        ContractType contractType = ContractType.valueOf(
                Objects.toString(row.get("contract_typ"), ""));
        return Optional.of(new DepositSnapshot(
                depositId,
                Objects.toString(row.get("fd_ref"), ""),
                contractType,
                Objects.toString(row.get("fd_sts"), ""),
                Boolean.TRUE.equals(row.get("contract_approved")),
                Boolean.TRUE.equals(row.get("profit_approved")),
                Boolean.TRUE.equals(row.get("evidence_reviewed")),
                Boolean.TRUE.equals(row.get("timeline_reviewed"))));
    }
}
