package com.cbms.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Prevent Islamic deposit products from entering conventional FD interest/GL workflows.
 * This guard is intentionally independent of Islamic contract-profile approval:
 * an approved contract profile is NOT an implemented profit/ledger engine.
 */
public final class BankIslamicDepositGuard {
    private BankIslamicDepositGuard() {}

    public static void requireConventional(JdbcTemplate jdbc, Long fdId, String operation) {
        List<String> types = jdbc.queryForList("""
                SELECT COALESCE(p.banking_typ, '')
                FROM public.bank_fixed_deposit fd
                JOIN public.bank_product p ON p.prod_id = fd.prod_id
                WHERE fd.fd_id = ?
                """, String.class, fdId);
        if (types.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Deposit or product not found");
        }
        if ("ISLAMIC".equalsIgnoreCase(types.get(0))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Islamic deposit " + operation + " requires a separate approved profit, settlement and ledger workflow. "
                    + "Conventional fixed-deposit processing is not permitted.");
        }
        if (!"CONVENTIONAL".equalsIgnoreCase(types.get(0))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Unsupported deposit banking classification; operation blocked");
        }
    }
}
