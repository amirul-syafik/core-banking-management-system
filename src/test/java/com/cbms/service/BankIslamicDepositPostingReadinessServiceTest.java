package com.cbms.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BankIslamicDepositPostingReadinessServiceTest {
    private static class ReadOnlyJdbc extends JdbcTemplate {
        boolean hasDeposit = true;
        boolean hasContract = true;
        boolean hasApprovedTerms = true;
        boolean hasReviewedEvidence = true;
        boolean hasReviewedMapping = true;
        boolean hasReviewedTimeline = true;
        String depositStatus = "DRAFT";
        int mutations;

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("FROM public.bank_fixed_deposit"))
                return hasDeposit ? List.of(Map.of("fd_sts", depositStatus)) : List.of();
            if (sql.contains("FROM public.bank_islamic_deposit_contract_snapshot"))
                return hasContract ? List.of(Map.of("profile_id", 9L, "contract_typ", "TAWARRUQ",
                        "profit_model", "DEFERRED_SALE")) : List.of();
            if (sql.contains("FROM public.bank_islamic_deposit_profit_terms"))
                return hasApprovedTerms ? List.of(Map.of("terms_id", 27L)) : List.of();
            if (sql.contains("FROM public.bank_islamic_deposit_execution_review"))
                return hasReviewedEvidence ? List.of(Map.of("execution_id", 32L)) : List.of();
            if (sql.contains("FROM public.bank_islamic_deposit_ledger_mapping_review"))
                return hasReviewedMapping ? List.of(Map.of("mapping_id", 40L)) : List.of();
            if (sql.contains("FROM public.bank_islamic_deposit_execution_timeline"))
                return hasReviewedTimeline ? List.of(Map.of("timeline_id", 41L)) : List.of();
            throw new IllegalArgumentException("Unexpected SQL in read-only preflight");
        }

        @Override
        public int update(String sql, Object... args) {
            mutations++;
            throw new AssertionError("Readiness must never update the database");
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            mutations++;
            throw new AssertionError("Readiness must not insert or post anything");
        }
    }

    @Test
    void evenFullyReviewedEvidenceNeverEnablesFinancialPosting() {
        ReadOnlyJdbc jdbc = new ReadOnlyJdbc();
        var report = new BankIslamicDepositPostingReadinessService(jdbc).inspect(5L);
        assertFalse(report.postingEnabled());
        assertEquals("TAWARRUQ", report.contractType());
        assertEquals(6, report.checks().stream().filter(c -> "PASSED".equals(c.status())).count());
        assertEquals(2, report.checks().stream().filter(c -> "BLOCKED".equals(c.status())).count());
        assertEquals(0, jdbc.mutations);
    }

    @Test
    void missingExternalChronologyReviewDoesNotEnablePosting() {
        ReadOnlyJdbc jdbc = new ReadOnlyJdbc();
        jdbc.hasReviewedTimeline = false;
        var report = new BankIslamicDepositPostingReadinessService(jdbc).inspect(5L);
        assertFalse(report.postingEnabled());
        assertTrue(report.checks().stream().anyMatch(c -> c.code().equals("DOCUMENTARY_SEQUENCE")
                && c.status().equals("PENDING")));
        assertEquals(0, jdbc.mutations);
    }

    @Test
    void missingProfitApprovalAlsoBlocksEvidenceReadiness() {
        ReadOnlyJdbc jdbc = new ReadOnlyJdbc();
        jdbc.hasApprovedTerms = false;
        var report = new BankIslamicDepositPostingReadinessService(jdbc).inspect(5L);
        assertFalse(report.postingEnabled());
        assertTrue(report.checks().stream().anyMatch(c -> c.code().equals("PROFIT_TERMS") && c.status().equals("PENDING")));
        assertTrue(report.checks().stream().anyMatch(c -> c.code().equals("EXECUTION_EVIDENCE") && c.status().equals("PENDING")));
        assertEquals(0, jdbc.mutations);
    }

    @Test
    void reviewedEvidenceWithoutGlMappingCannotEnablePosting() {
        ReadOnlyJdbc jdbc = new ReadOnlyJdbc();
        jdbc.hasReviewedMapping = false;
        var report = new BankIslamicDepositPostingReadinessService(jdbc).inspect(5L);
        assertFalse(report.postingEnabled());
        assertTrue(report.checks().stream().anyMatch(c -> c.code().equals("LEDGER_MAPPING")
                && c.status().equals("PENDING")));
        assertEquals(0, jdbc.mutations);
    }

    @Test
    void missingContractSnapshotCannotBeConsideredReady() {
        ReadOnlyJdbc jdbc = new ReadOnlyJdbc();
        jdbc.hasContract = false;
        var report = new BankIslamicDepositPostingReadinessService(jdbc).inspect(5L);
        assertFalse(report.postingEnabled());
        assertNull(report.contractType());
        assertTrue(report.checks().stream().anyMatch(c -> c.code().equals("CONTRACT_SNAPSHOT") && c.status().equals("BLOCKED")));
        assertEquals(0, jdbc.mutations);
    }

    @Test
    void nonDraftDepositIsBlocked() {
        ReadOnlyJdbc jdbc = new ReadOnlyJdbc();
        jdbc.depositStatus = "ACTIVE";
        var report = new BankIslamicDepositPostingReadinessService(jdbc).inspect(5L);
        assertFalse(report.postingEnabled());
        assertTrue(report.checks().stream().anyMatch(c -> c.code().equals("DEPOSIT_DRAFT") && c.status().equals("BLOCKED")));
    }
}
