-- CBMS Financial Integration Test Fixture
-- WARNING: destructive test-data reset. ONLY corebank_lite_test.
-- Compatible with PostgreSQL; do not run against development/production.

SELECT 1 / CASE
               WHEN current_database() = 'corebank_lite_test' THEN 1
               ELSE 0
    END;

-- Remove ledger entries of any reversal of the fixture deposit first.
DELETE
FROM public.bank_ledger_entry
WHERE txn_id IN (SELECT r.txn_id
                 FROM public.bank_transaction r
                          JOIN public.bank_transaction o ON o.txn_id = r.reversal_of_txn_id
                 WHERE o.txn_ref = 'TEST-DEP-900001');

-- Remove audit records referencing the fixture deposit or its reversals.
DELETE
FROM public.bank_transaction_reversal_audit
WHERE original_txn_id IN (SELECT txn_id FROM public.bank_transaction WHERE txn_ref = 'TEST-DEP-900001')
   OR reversal_txn_id IN (SELECT r.txn_id
                          FROM public.bank_transaction r
                                   JOIN public.bank_transaction o ON o.txn_id = r.reversal_of_txn_id
                          WHERE o.txn_ref = 'TEST-DEP-900001');

-- Remove the reversal transactions before their original transaction.
DELETE
FROM public.bank_transaction
WHERE reversal_of_txn_id IN (SELECT txn_id FROM public.bank_transaction WHERE txn_ref = 'TEST-DEP-900001');

-- Remove ledger entries for all transactions tied to the test account.
DELETE
FROM public.bank_ledger_entry
WHERE txn_id IN (SELECT txn_id
                 FROM public.bank_transaction
                 WHERE acct_id IN (SELECT acct_id
                                   FROM public.bank_account
                                   WHERE cust_id = -900001)
                    OR dest_acct_id IN (SELECT acct_id
                                        FROM public.bank_account
                                        WHERE cust_id = -900001));

-- Remove reversal audits referencing any remaining test-account transactions.
DELETE
FROM public.bank_transaction_reversal_audit
WHERE original_txn_id IN (SELECT txn_id
                          FROM public.bank_transaction
                          WHERE acct_id IN (SELECT acct_id FROM public.bank_account WHERE cust_id = -900001)
                             OR dest_acct_id IN (SELECT acct_id FROM public.bank_account WHERE cust_id = -900001))
   OR reversal_txn_id IN (SELECT txn_id
                          FROM public.bank_transaction
                          WHERE acct_id IN (SELECT acct_id FROM public.bank_account WHERE cust_id = -900001)
                             OR dest_acct_id IN (SELECT acct_id FROM public.bank_account WHERE cust_id = -900001));

-- Delete any other test-account transactions, newest first for self-FK safety.
DELETE
FROM public.bank_transaction
WHERE reversal_of_txn_id IN (SELECT txn_id
                             FROM public.bank_transaction
                             WHERE acct_id IN (SELECT acct_id FROM public.bank_account WHERE cust_id = -900001)
                                OR dest_acct_id IN (SELECT acct_id FROM public.bank_account WHERE cust_id = -900001));

DELETE
FROM public.bank_transaction
WHERE acct_id IN (SELECT acct_id FROM public.bank_account WHERE cust_id = -900001)
   OR dest_acct_id IN (SELECT acct_id FROM public.bank_account WHERE cust_id = -900001);

DELETE
FROM public.bank_account_balance
WHERE acct_id IN (SELECT acct_id FROM public.bank_account WHERE cust_id = -900001);

DELETE
FROM public.bank_account_audit_history
WHERE acct_id IN (SELECT acct_id FROM public.bank_account WHERE cust_id = -900001);

DELETE
FROM public.bank_account
WHERE cust_id = -900001;
DELETE
FROM public.customer_master
WHERE cust_id = -900001;

-- Ensure the two GL accounts used by deposit and reversal posting exist.
INSERT INTO public.bank_ledger_account (gl_cd, gl_nm, gl_typ, ccy_cd, act_flg)
VALUES ('100100', 'Cash Control', 'ASSET', 'MYR', TRUE),
       ('200100', 'Customer Deposits', 'LIABILITY', 'MYR', TRUE) ON CONFLICT (gl_cd) DO NOTHING;

-- Customer master has a GENERATED ALWAYS identity cust_id.
INSERT INTO public.customer_master (cust_id, cust_nm, cust_typ, sts, crt_by, upd_by)
    OVERRIDING SYSTEM VALUE
VALUES (-900001, 'CBMS Integration Test Customer', 'INDIVIDUAL',
        'ACTIVE', 'INTEGRATION_TEST', 'INTEGRATION_TEST');

INSERT INTO public.bank_account (cust_id, acct_no, acct_typ, ccy_cd, acct_sts, crt_by, upd_by)
VALUES (-900001, 'TEST-REV-900001', 'SAVINGS', 'MYR',
        'ACTIVE', 'INTEGRATION_TEST', 'INTEGRATION_TEST');

INSERT INTO public.bank_account_balance (acct_id, ledger_bal, avail_bal, upd_by)
SELECT acct_id, 100.00, 100.00, 'INTEGRATION_TEST'
FROM public.bank_account
WHERE acct_no = 'TEST-REV-900001';

INSERT INTO public.bank_transaction (txn_ref, acct_id, txn_typ, txn_amt, ccy_cd, txn_sts,
                                     idempotency_key, crt_by)
SELECT 'TEST-DEP-900001',
       acct_id,
       'DEPOSIT',
       100.00,
       'MYR',
       'POSTED',
       'TEST-IDEMP-DEP-900001',
       'INTEGRATION_TEST'
FROM public.bank_account
WHERE acct_no = 'TEST-REV-900001';

-- Deposit: debit cash asset GL, credit customer deposit liability GL.
INSERT INTO public.bank_ledger_entry (txn_id, gl_id, acct_id, entry_side, entry_amt)
SELECT t.txn_id, g.gl_id, NULL, 'DEBIT', 100.00
FROM public.bank_transaction t
         JOIN public.bank_ledger_account g ON g.gl_cd = '100100'
WHERE t.txn_ref = 'TEST-DEP-900001';

INSERT INTO public.bank_ledger_entry (txn_id, gl_id, acct_id, entry_side, entry_amt)
SELECT t.txn_id, g.gl_id, t.acct_id, 'CREDIT', 100.00
FROM public.bank_transaction t
         JOIN public.bank_ledger_account g ON g.gl_cd = '200100'
WHERE t.txn_ref = 'TEST-DEP-900001';
