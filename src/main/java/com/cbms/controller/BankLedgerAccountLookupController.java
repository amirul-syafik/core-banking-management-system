package com.cbms.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Read-only catalog for selecting configured, active MYR ledger accounts. Never posts GL entries. */
@RestController
@RequestMapping("/api/ledger-accounts")
public class BankLedgerAccountLookupController {
    private static final int PAGE_SIZE = 10;
    private final JdbcTemplate jdbc;

    public BankLedgerAccountLookupController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record LedgerOption(String glCode, String glName, String glType, String currency) {}
    public record LookupPage(List<LedgerOption> content, long totalElements, int page, int totalPages) {}

    @GetMapping("/eligible")
    public LookupPage eligible(@RequestParam(defaultValue = "") String query,
                               @RequestParam(defaultValue = "0") int page) {
        if (page < 0 || page > 100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        String term = query == null ? "" : query.trim();
        if (term.length() > 80) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search term is too long");
        // Escape LIKE wildcards so a search never inadvertently retrieves the entire catalog.
        String search = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        String criteria = " FROM public.bank_ledger_account " +
                "WHERE ccy_cd = 'MYR' AND act_flg = TRUE " +
                "AND (gl_cd ILIKE ? ESCAPE '!' OR gl_nm ILIKE ? ESCAPE '!')";
        Long count = jdbc.queryForObject("SELECT COUNT(*) " + criteria,
                Long.class, search, search);
        long total = count == null ? 0 : count;
        List<LedgerOption> rows = jdbc.query("SELECT gl_cd, gl_nm, gl_typ, ccy_cd " + criteria +
                        " ORDER BY gl_cd LIMIT ? OFFSET ?",
                (rs, index) -> new LedgerOption(rs.getString("gl_cd"), rs.getString("gl_nm"),
                        rs.getString("gl_typ"), rs.getString("ccy_cd")),
                search, search, PAGE_SIZE, page * PAGE_SIZE);
        return new LookupPage(rows, total, page, (int) Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE));
    }
}
