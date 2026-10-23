DO
$$
BEGIN
    IF
current_database() <> 'corebank_lite_test' THEN
        RAISE EXCEPTION
            'Rollback trigger must only run in corebank_lite_test';
END IF;
END
$$
@@

CREATE OR REPLACE FUNCTION public.cbms_test_fail_ledger_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF
NEW.entry_side = 'CREDIT'
       AND NEW.entry_amt = 500.00
       AND EXISTS (
           SELECT 1
           FROM public.bank_transaction t
           WHERE t.txn_id = NEW.txn_id
             AND t.txn_typ = 'DEPOSIT'
             AND t.crt_by = 'integration-test-maker'
       )
    THEN
        RAISE EXCEPTION
            'CBMS_TEST_FORCED_LEDGER_FAILURE';
END IF;

RETURN NEW;
END;
$$
@@

DROP TRIGGER IF EXISTS
    trg_cbms_test_fail_ledger_insert
ON public.bank_ledger_entry
@@

CREATE TRIGGER trg_cbms_test_fail_ledger_insert
BEFORE INSERT ON public.bank_ledger_entry
FOR EACH ROW
EXECUTE FUNCTION public.cbms_test_fail_ledger_insert()
@@
