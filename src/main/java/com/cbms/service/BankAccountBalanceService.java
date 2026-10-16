package com.cbms.service;

import com.cbms.dto.BankAccountBalanceResponse;
import com.cbms.entity.BankAccount;
import com.cbms.entity.BankAccountBalance;
import com.cbms.repository.BankAccountRepository;
import com.cbms.repository.BankAccountBalanceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankAccountBalanceService {

    private final BankAccountRepository accountRepository;
    private final BankAccountBalanceRepository balanceRepository;

    public BankAccountBalanceService(
            BankAccountRepository accountRepository,
            BankAccountBalanceRepository balanceRepository
    ) {
        this.accountRepository = accountRepository;
        this.balanceRepository = balanceRepository;
    }

    @Transactional(readOnly = true)
    public BankAccountBalanceResponse getBalance(Long acctId) {

        BankAccount account = accountRepository.findById(acctId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Bank account not found: " + acctId
                        )
                );

        BankAccountBalance balance = balanceRepository.findById(acctId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Balance record missing for account: " + acctId
                        )
                );

        return new BankAccountBalanceResponse(
                account.getAcctId(),
                account.getAcctNo(),
                account.getCcyCd(),
                balance.getLedgerBal(),
                balance.getAvailBal(),
                balance.getUpdDt(),
                balance.getUpdBy()
        );
    }
}
