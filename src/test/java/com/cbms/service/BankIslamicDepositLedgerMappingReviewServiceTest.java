package com.cbms.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BankIslamicDepositLedgerMappingReviewServiceTest {
    private static class FakeJdbc extends JdbcTemplate {
        boolean hasReviewedEvidence = true;
        boolean glAvailable = true;
        int inserts;
        int otherMutations;
        @Override public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("FROM public.bank_islamic_deposit_execution_review e")) {
                return hasReviewedEvidence ? List.of(Map.of(
                        "execution_id", 11L, "contract_typ", "TAWARRUQ", "accounting_policy_ref", "POL-TEST",
                        "ledger_mapping_ref", "MAP-TEST", "fd_sts", "DRAFT")) : List.of();
            }
            if (sql.contains("FROM public.bank_ledger_account")) {
                if (!glAvailable) return List.of();
                List<Map<String, Object>> found = new ArrayList<>();
                for (Object value : args) found.add(Map.of("gl_cd", value));
                return found;
            }
            if (sql.contains("FROM public.bank_islamic_deposit_ledger_mapping_review m")) {
                return List.of(Map.of("mapping_id", 9L, "mapping_ref", "ILM-2026-ABCDEF123456",
                        "mapping_sts", "SUBMITTED", "crt_by", "maker", "fd_sts", "DRAFT"));
            }
            if (sql.contains("FROM public.bank_islamic_deposit_ledger_mapping_review WHERE mapping_id")) {
                return List.of(Map.of("mapping_id", 9L, "mapping_ref", "ILM-2026-ABCDEF123456",
                        "mapping_sts", "DRAFT", "crt_by", "maker"));
            }
            throw new AssertionError("Unexpected SQL: " + sql);
        }
        @Override public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            if (!sql.contains("bank_islamic_deposit_ledger_mapping_review")) throw new AssertionError("Unexpected write");
            inserts++;
            return type.cast(9L);
        }
        @Override public int update(String sql, Object... args) {
            otherMutations++;
            throw new AssertionError("Should not update any account balances, journals, or statuses in this test");
        }
    }
    private static final BankIslamicDepositLedgerMappingReviewService.Proposal VALID =
            new BankIslamicDepositLedgerMappingReviewService.Proposal("100001", "200001", "500001", "210001",
                    "210001", "100001", "200001", "100001");

    private static UsernamePasswordAuthenticationToken actor(String name) {
        return new UsernamePasswordAuthenticationToken(name, "unused", List.of());
    }
    @Test void validProposalRecordsOnlyNonPostingMapping() {
        FakeJdbc jdbc = new FakeJdbc();
        var result = new BankIslamicDepositLedgerMappingReviewService(jdbc).create(5L, VALID, actor("maker"));
        assertEquals("ILM-2026-ABCDEF123456", result.get("mapping_ref"));
        assertEquals(1, jdbc.inserts);
        assertEquals(0, jdbc.otherMutations);
    }
    @Test void missingReviewedEvidenceBlocksMapping() {
        FakeJdbc jdbc = new FakeJdbc(); jdbc.hasReviewedEvidence = false;
        assertThrows(ResponseStatusException.class,
                () -> new BankIslamicDepositLedgerMappingReviewService(jdbc).create(5L, VALID, actor("maker")));
        assertEquals(0, jdbc.inserts);
    }
    @Test void sameDebitAndCreditIsRejected() {
        FakeJdbc jdbc = new FakeJdbc();
        var invalid = new BankIslamicDepositLedgerMappingReviewService.Proposal("100001", "100001", "500001", "210001",
                "210001", "100001", "200001", "100001");
        assertThrows(ResponseStatusException.class,
                () -> new BankIslamicDepositLedgerMappingReviewService(jdbc).create(5L, invalid, actor("maker")));
        assertEquals(0, jdbc.inserts);
    }
    @Test void unavailableGlAccountIsRejected() {
        FakeJdbc jdbc = new FakeJdbc(); jdbc.glAvailable = false;
        assertThrows(ResponseStatusException.class,
                () -> new BankIslamicDepositLedgerMappingReviewService(jdbc).create(5L, VALID, actor("maker")));
        assertEquals(0, jdbc.inserts);
    }
    @Test void makerCannotApproveOwnSubmission() {
        FakeJdbc jdbc = new FakeJdbc();
        assertThrows(ResponseStatusException.class,
                () -> new BankIslamicDepositLedgerMappingReviewService(jdbc).decide(9L,
                        new BankIslamicDepositLedgerMappingReviewService.Decision("ACCEPT", "REV-TEST", ""), actor("maker")));
        assertEquals(0, jdbc.otherMutations);
    }
}
