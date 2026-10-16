package com.cbms.service;

import com.cbms.dto.BankAccountCreateRequest;
import com.cbms.dto.BankAccountResponse;
import com.cbms.entity.BankAccount;
import com.cbms.entity.Customer;
import com.cbms.repository.BankAccountRepository;
import com.cbms.repository.CustomerRepository;

import jakarta.persistence.EntityManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import com.cbms.entity.BankAccountBalance;
import com.cbms.repository.BankAccountBalanceRepository;
import java.math.BigDecimal;

@Service
public class BankAccountService {

    private final BankAccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final EntityManager entityManager;
    private final BankAccountBalanceRepository balanceRepository;

    public BankAccountService(
            BankAccountRepository accountRepository,
            CustomerRepository customerRepository,
            EntityManager entityManager,
            BankAccountBalanceRepository balanceRepository
    ) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.entityManager = entityManager;
        this.balanceRepository = balanceRepository;
    }

    @Transactional
    public BankAccountResponse create(
            Long custId,
            BankAccountCreateRequest request,
            Authentication authentication
    ) {
        Customer customer = customerRepository.findById(custId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Customer not found: " + custId
                        )
                );

        if (!"ACTIVE".equals(customer.getSts())) {
            throw new IllegalArgumentException(
                    "Only ACTIVE customers can open bank accounts"
            );
        }

        Number sequenceValue = (Number) entityManager
                .createNativeQuery(
                        "SELECT nextval('public.bank_account_no_seq')"
                )
                .getSingleResult();

        String accountNumber = sequenceValue.toString();

        OffsetDateTime now = OffsetDateTime.now();

        BankAccount account = new BankAccount();
        account.setCustId(custId);
        account.setAcctNo(accountNumber);
        account.setAcctTyp(request.acctTyp());
        account.setCcyCd(request.ccyCd());
        account.setAcctSts("PENDING");
        account.setOpenDt(LocalDate.now());
        account.setCrtDt(now);
        account.setUpdDt(now);
        account.setCrtBy(authentication.getName());
        account.setUpdBy(authentication.getName());

        BankAccount saved = accountRepository.save(account);

        BankAccountBalance balance = new BankAccountBalance();
        balance.setAcctId(saved.getAcctId());
        balance.setLedgerBal(new BigDecimal("0.00"));
        balance.setAvailBal(new BigDecimal("0.00"));
        balance.setUpdDt(now);
        balance.setUpdBy(authentication.getName());

        balanceRepository.save(balance);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BankAccountResponse get(Long acctId) {
        BankAccount account = accountRepository.findById(acctId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Bank account not found: " + acctId
                        )
                );

        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> listByCustomer(Long custId) {

        if (!customerRepository.existsById(custId)) {
            throw new IllegalArgumentException(
                    "Customer not found: " + custId
            );
        }

        return accountRepository
                .findByCustIdOrderByAcctIdDesc(custId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private BankAccountResponse toResponse(BankAccount account) {
        return new BankAccountResponse(
                account.getAcctId(),
                account.getCustId(),
                account.getAcctNo(),
                account.getAcctTyp(),
                account.getCcyCd(),
                account.getAcctSts(),
                account.getOpenDt(),
                account.getCloseDt(),
                account.getCrtBy(),
                account.getCrtDt()
        );
    }
}
