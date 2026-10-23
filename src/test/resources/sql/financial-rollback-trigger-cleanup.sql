DO
$$
BEGIN
    IF
current_database() <> 'corebank_lite_test' THEN
        RAISE EXCEPTION
            'Cleanup must only run in corebank_lite_test';
END IF;
END
$$
@@

DROP TRIGGER IF EXISTS
    trg_cbms_test_fail_ledger_insert
ON public.bank_ledger_entry
@@

DROP FUNCTION IF EXISTS
    public.cbms_test_fail_ledger_insert()
@@
