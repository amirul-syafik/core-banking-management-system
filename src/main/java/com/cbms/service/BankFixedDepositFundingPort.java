package com.cbms.service;

import java.math.BigDecimal;

public interface BankFixedDepositFundingPort {

    /**
     * Debit the source account through the existing
     * account/transaction/ledger posting implementation.
     * <p>
     * Must:
     * 1. Lock the source account.
     * 2. Validate ownership and ACTIVE status.
     * 3. Validate MYR currency.
     * 4. Check available funds.
     * 5. Debit the account and create required posting entries.
     * 6. Return a unique, durable posting reference.
     * <p>
     * All writes must participate in the caller's transaction.
     */
    String debitForPlacement(
            Long sourceAccountId,
            Long customerId,
            BigDecimal amount,
            String placementReference,
            String username
    );
}