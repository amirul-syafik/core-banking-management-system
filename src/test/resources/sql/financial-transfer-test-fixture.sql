SELECT 1 / CASE
               WHEN current_database() = 'corebank_lite_test' THEN 1
               ELSE 0
    END;

-- Clean up previous financial lifecycle test records.
DELETE
FROM public.bank_ledger_entry
WHERE txn_id IN (SELECT txn_id
                 FROM public.bank_transaction
                 WHERE acct_id IN (SELECT acct_id
                                   FROM public.bank_account
                                   WHERE acct_no IN ('TEST-LIFE-A', 'TEST-LIFE-B'))
                    OR dest_acct_id IN (SELECT acct_id
                                        FROM public.bank_account
                                        WHERE acct_no IN ('TEST-LIFE-A', 'TEST-LIFE-B')));

DELETE
FROM public.bank_transaction_reversal_audit
WHERE original_txn_id IN (SELECT txn_id
                          FROM public.bank_transaction
                          WHERE acct_id IN (SELECT acct_id
                                            FROM public.bank_account
                                            WHERE acct_no IN ('TEST-LIFE-A', 'TEST-LIFE-B')));

DELETE
FROM public.bank_transaction
WHERE acct_id IN (SELECT acct_id
                  FROM public.bank_account
                  WHERE acct_no IN ('TEST-LIFE-A', 'TEST-LIFE-B'))
   OR dest_acct_id IN (SELECT acct_id
                       FROM public.bank_account
                       WHERE acct_no IN ('TEST-LIFE-A', 'TEST-LIFE-B'));

DELETE
FROM public.bank_account_balance
WHERE acct_id IN (SELECT acct_id
                  FROM public.bank_account
                  WHERE acct_no IN ('TEST-LIFE-A', 'TEST-LIFE-B'));

DELETE
FROM public.bank_account
WHERE acct_no IN ('TEST-LIFE-A', 'TEST-LIFE-B');

DELETE
FROM public.customer_master
WHERE cust_id = -900002;

-- Create a dedicated ACTIVE customer.
INSERT INTO public.customer_master (cust_id, cust_nm, cust_typ, sts, crt_by, upd_by)
    OVERRIDING SYSTEM VALUE
VALUES (-900002,
        'CBMS Financial Lifecycle Test Customer',
        'INDIVIDUAL',
        'ACTIVE',
        'INTEGRATION_TEST',
        'INTEGRATION_TEST');

-- Create two ACTIVE savings accounts.
INSERT INTO public.bank_account (cust_id, acct_no, acct_typ, ccy_cd,
                                 acct_sts, crt_by, upd_by)
VALUES (-900002, 'TEST-LIFE-A', 'SAVINGS', 'MYR',
        'ACTIVE', 'INTEGRATION_TEST', 'INTEGRATION_TEST'),
       (-900002, 'TEST-LIFE-B', 'SAVINGS', 'MYR',
        'ACTIVE', 'INTEGRATION_TEST', 'INTEGRATION_TEST');

-- Create zero balances for both accounts.
INSERT INTO public.bank_account_balance (acct_id, ledger_bal, avail_bal, upd_by)
SELECT acct_id, 0.00, 0.00, 'INTEGRATION_TEST'
FROM public.bank_account
WHERE acct_no IN ('TEST-LIFE-A', 'TEST-LIFE-B');
