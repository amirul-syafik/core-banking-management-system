package com.cbms.service;

import com.cbms.dto.BankFixedDepositCreateRequest;
import com.cbms.dto.BankFixedDepositResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** An Islamic draft is not placement or a profit posting. */
class BankFixedDepositIslamicDraftCreationTest {

    private static final class DraftDb extends JdbcTemplate {
        boolean islamic = true;
        boolean floating = true;
        boolean approved = true;
        int depositInserts;
        int contractSnapshots;

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("FROM public.bank_product")) {
                return List.of(Map.of("prod_id", 3L, "prod_cat", "FIXED_DEPOSIT",
                        "prod_sts", "ACTIVE", "banking_typ", islamic ? "ISLAMIC" : "CONVENTIONAL",
                        "ccy_cd", "MYR"));
            }
            if (sql.contains("FROM public.bank_scheme_rate")) {
                return floating
                        ? List.of(Map.of("rate_typ", "FLOATING", "base_rate_cd", "BR",
                            "margin_pct", new BigDecimal("1.00")))
                        : List.of(Map.of("rate_typ", "FIXED", "fixed_rate_pct", new BigDecimal("6.00"),
                            "min_rate_pct", new BigDecimal("6.00"), "max_rate_pct", new BigDecimal("6.00")));
            }
            if (sql.contains("FROM public.bank_scheme")) {
                return List.of(Map.of("prod_id", 3L, "schm_sts", "ACTIVE",
                        "min_amt", new BigDecimal("100.00"), "max_amt", new BigDecimal("10000.00"),
                        "min_tenor", 1, "max_tenor", 36));
            }
            if (sql.contains("FROM public.bank_islamic_contract_profile")) {
                if (!approved) return List.of();
                Map<String, Object> profile = new HashMap<>();
                profile.put("profile_id", 1L);
                profile.put("schm_id", 11L);
                profile.put("prod_id", 3L);
                profile.put("contract_typ", "TAWARRUQ");
                profile.put("profit_model", "DEFERRED_SALE");
                profile.put("terms_summary", "Contract terms approved for draft processing only");
                profile.put("shariah_approval_ref", "SH-2026-001");
                profile.put("decided_by", "independent_checker");
                profile.put("decided_at", Timestamp.from(Instant.parse("2026-11-13T02:00:00Z")));
                return List.of(profile);
            }
            return List.of();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> List<T> queryForList(String sql, Class<T> type, Object... args) {
            if (sql.contains("FROM public.customer_master")) return (List<T>) List.of("ACTIVE");
            return List.of();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            if (sql.contains("INSERT INTO public.bank_fixed_deposit")) {
                depositInserts++;
                return (T) Long.valueOf(88L);
            }
            return null;
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.contains("INSERT INTO public.bank_islamic_deposit_contract_snapshot")) {
                contractSnapshots++;
                return 1;
            }
            return 0;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
            if (sql.contains("FROM public.bank_fixed_deposit")) {
                return (List<T>) List.of(new BankFixedDepositResponse(88L, "FD-88", 5L, 3L,
                        11L, "FIXED_DEPOSIT", "MYR", new BigDecimal("7000.00"), 36,
                        LocalDate.now(), LocalDate.now().plusMonths(36),
                        floating ? "FLOATING" : "FIXED", new BigDecimal("6.00"), "DRAFT", ""));
            }
            return List.of();
        }
    }

    private BankFixedDepositService service(DraftDb db) {
        var floating = new BankFloatingDepositRateService(db) {
            @Override
            public BigDecimal rateOn(String code, BigDecimal margin, LocalDate date) {
                assertEquals("BR", code);
                assertEquals(0, margin.compareTo(new BigDecimal("1.00")));
                return new BigDecimal("6.00");
            }
        };
        return new BankFixedDepositService(db, new BankFixedDepositMaturityService(db),
                floating, new BankIslamicDepositDraftService(db));
    }

    private BankFixedDepositCreateRequest request(String rate) {
        return new BankFixedDepositCreateRequest(5L, 3L, 11L, "FIXED_DEPOSIT",
                new BigDecimal("7000.00"), 36, new BigDecimal(rate), LocalDate.now(), "");
    }

    private Authentication maker() {
        return new UsernamePasswordAuthenticationToken("maker", null, List.of());
    }

    @Test
    void approvedIslamicFloatingRateSchemeCreatesOnlyDraftAndSnapshot() {
        DraftDb db = new DraftDb();
        BankFixedDepositResponse result = service(db).create(request("6.00"), maker());
        assertEquals("DRAFT", result.status());
        assertEquals(1, db.depositInserts);
        assertEquals(1, db.contractSnapshots);
    }

    @Test
    void missingApprovedContractRejectsBeforeCreatingDeposit() {
        DraftDb db = new DraftDb();
        db.approved = false;
        assertThrows(ResponseStatusException.class, () -> service(db).create(request("6.00"), maker()));
        assertEquals(0, db.depositInserts);
        assertEquals(0, db.contractSnapshots);
    }

    @Test
    void mismatchedFloatingReferenceRateRemainsRejected() {
        DraftDb db = new DraftDb();
        assertThrows(ResponseStatusException.class, () -> service(db).create(request("5.00"), maker()));
        assertEquals(0, db.depositInserts);
    }

    @Test
    void conventionalFloatingRateCreationIsUnaffected() {
        DraftDb db = new DraftDb();
        db.islamic = false;
        db.approved = false;
        assertEquals("DRAFT", service(db).create(request("6.00"), maker()).status());
        assertEquals(1, db.depositInserts);
        assertEquals(0, db.contractSnapshots);
    }

    @Test
    void islamicFixedRateDraftCreationIsUnaffected() {
        DraftDb db = new DraftDb();
        db.floating = false;
        assertEquals("DRAFT", service(db).create(request("6.00"), maker()).status());
        assertEquals(1, db.depositInserts);
        assertEquals(1, db.contractSnapshots);
    }
}
