package com.cbms.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.cbms.entity.BankAccountBalance;

import java.math.BigDecimal;

public interface AccountReconciliationRepository
        extends Repository<BankAccountBalance, Long> {

    @Query(value = """
        SELECT COALESCE(
            SUM(
                CASE
                    WHEN e.entry_side = 'CREDIT'
                        THEN e.entry_amt
                    WHEN e.entry_side = 'DEBIT'
                        THEN -e.entry_amt
                    ELSE 0
                END
            ),
            0
        )
        FROM public.bank_ledger_entry e
        JOIN public.bank_ledger_account g
            ON g.gl_id = e.gl_id
        JOIN public.bank_transaction t
            ON t.txn_id = e.txn_id
        WHERE e.acct_id = :acctId
          AND g.gl_cd = '200100'
          AND t.txn_sts = 'POSTED'
        """, nativeQuery = true)
    BigDecimal calculateLedgerBalance(
            @Param("acctId") Long acctId
    );
}
