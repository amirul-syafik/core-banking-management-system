package com.cbms.service;

import com.cbms.dto.AccountReconciliationResponse;
import com.cbms.entity.BankAccount;
import com.cbms.entity.BankAccountBalance;
import com.cbms.repository.AccountReconciliationRepository;
import com.cbms.repository.BankAccountBalanceRepository;
import com.cbms.repository.BankAccountRepository;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AccountReconciliationService {

    private final BankAccountRepository accountRepository;
    private final BankAccountBalanceRepository balanceRepository;
    private final AccountReconciliationRepository reconciliationRepository;

    public AccountReconciliationService(
            BankAccountRepository accountRepository,
            BankAccountBalanceRepository balanceRepository,
            AccountReconciliationRepository reconciliationRepository
    ) {
        this.accountRepository = accountRepository;
        this.balanceRepository = balanceRepository;
        this.reconciliationRepository = reconciliationRepository;
    }

    /**
     * Read-only, paginated reconciliation of real accounts; never updates balances.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<AccountReconciliationResponse> search(String accountNo, int page, int size) {
        if (page < 0 || size < 1 || size > 20) {
            throw new IllegalArgumentException("Invalid reconciliation page or size");
        }
        String query = accountNo == null ? "" : accountNo.trim();
        if (query.length() > 30) {
            throw new IllegalArgumentException("Account number search is too long");
        }
        return accountRepository.searchAccounts(query, "", "",
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "acctId")))
                .map(account -> reconcile(account.getAcctId()));
    }

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public AccountReconciliationResponse reconcile(Long acctId) {

        BankAccount account = accountRepository.findById(acctId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Bank account not found: " + acctId
                        )
                );

        BankAccountBalance balance = balanceRepository.findById(acctId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Balance record missing: " + acctId
                        )
                );

        BigDecimal calculatedBalance =
                reconciliationRepository.calculateLedgerBalance(acctId);

        BigDecimal difference = balance.getLedgerBal()
                .subtract(calculatedBalance);

        return new AccountReconciliationResponse(
                acctId,
                account.getAcctNo(),
                account.getCcyCd(),
                balance.getLedgerBal(),
                calculatedBalance,
                difference,
                difference.compareTo(BigDecimal.ZERO) == 0
        );
    }
}
