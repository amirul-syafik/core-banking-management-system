package com.cbms.service;

import com.cbms.dto.AccountReconciliationResponse;
import com.cbms.entity.BankAccount;
import com.cbms.entity.BankAccountBalance;
import com.cbms.repository.AccountReconciliationRepository;
import com.cbms.repository.BankAccountBalanceRepository;
import com.cbms.repository.BankAccountRepository;

import org.springframework.stereotype.Service;
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
