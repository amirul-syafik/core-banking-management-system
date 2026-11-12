-- FD maturity principal settlement: extend transaction type and destination constraints.
-- Apply to corebank_lite_test and corebank_lite before enabling the new endpoint.
-- Existing allowed transaction types remain unchanged.
ALTER TABLE public.bank_transaction DROP CONSTRAINT IF EXISTS chk_bank_txn_type;
ALTER TABLE public.bank_transaction ADD CONSTRAINT chk_bank_txn_type CHECK (
  txn_typ IN ('DEPOSIT','WITHDRAWAL','TRANSFER','REVERSAL',
              'FD_PLACEMENT','FD_INTEREST_ACCRUAL','FD_INTEREST_PAYOUT',
              'FD_RENEWAL','FD_PREMATURE_WITHDRAWAL','FD_MATURITY_SETTLEMENT')
);
ALTER TABLE public.bank_transaction DROP CONSTRAINT IF EXISTS chk_bank_txn_transfer_destination;
ALTER TABLE public.bank_transaction ADD CONSTRAINT chk_bank_txn_transfer_destination CHECK (
  (txn_typ = 'TRANSFER' AND dest_acct_id IS NOT NULL
     AND dest_acct_id <> acct_id AND reversal_of_txn_id IS NULL)
  OR (txn_typ = 'REVERSAL' AND reversal_of_txn_id IS NOT NULL
     AND (dest_acct_id IS NULL OR dest_acct_id <> acct_id))
  OR (txn_typ IN ('DEPOSIT','WITHDRAWAL',
                  'FD_PLACEMENT','FD_INTEREST_ACCRUAL','FD_INTEREST_PAYOUT',
                  'FD_RENEWAL','FD_PREMATURE_WITHDRAWAL','FD_MATURITY_SETTLEMENT')
      AND dest_acct_id IS NULL AND reversal_of_txn_id IS NULL)
);
