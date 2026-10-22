package com.cbms.entity;

/**
 * Transaction types allowed by chk_bank_txn_type in public.bank_transaction.
 */
public enum BankTransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    REVERSAL,
    FD_PLACEMENT,
    FD_INTEREST_ACCRUAL,
    FD_INTEREST_PAYOUT,
    FD_RENEWAL,
    FD_PREMATURE_WITHDRAWAL;

    /**
     * Unknown historical values remain displayable instead of failing a history page.
     */
    public static BankTransactionType fromDatabase(String value) {
        if (value == null) return null;
        try {
            return BankTransactionType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
