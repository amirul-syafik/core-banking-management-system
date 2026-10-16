package com.cbms.service;

import com.cbms.dto.BankPostingResponse;
import com.cbms.entity.*;
import com.cbms.repository.*;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class BankPostingService {

    private final BankAccountRepository accountRepository;
    private final BankAccountBalanceRepository balanceRepository;
    private final BankTransactionRepository transactionRepository;
    private final BankLedgerAccountRepository glRepository;
    private final BankLedgerEntryRepository entryRepository;
    private final IdempotencyLockRepository idempotencyLockRepository;

    public BankPostingService(
            BankAccountRepository accountRepository,
            BankAccountBalanceRepository balanceRepository,
            BankTransactionRepository transactionRepository,
            BankLedgerAccountRepository glRepository,
            BankLedgerEntryRepository entryRepository,
            IdempotencyLockRepository idempotencyLockRepository
    ) {
        this.accountRepository = accountRepository;
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
        this.glRepository = glRepository;
        this.entryRepository = entryRepository;
        this.idempotencyLockRepository = idempotencyLockRepository;
    }

    @Transactional
    public BankPostingResponse post(
            Long acctId,
            String type,
            BigDecimal amount,
            String idempotencyKey,
            Authentication authentication
    ) {
        if (idempotencyKey == null ||
                idempotencyKey.isBlank() ||
                idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "A valid Idempotency-Key header is required"
            );
        }

        if (!"DEPOSIT".equals(type) && !"WITHDRAWAL".equals(type)) {
            throw new IllegalArgumentException("Unsupported transaction type");
        }

        if (amount == null ||
                amount.signum() <= 0 ||
                amount.scale() > 2 ||
                amount.precision() - amount.scale() > 17) {
            throw new IllegalArgumentException("Invalid transaction amount");
        }

        idempotencyLockRepository.lock(idempotencyKey);

        BankTransaction existing = transactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElse(null);

        if (existing != null) {
            if (!existing.getAcctId().equals(acctId) ||
                    !existing.getTxnTyp().equals(type) ||
                    existing.getTxnAmt().compareTo(amount) != 0) {
                throw new IllegalArgumentException(
                        "Idempotency key already used for a different request"
                );
            }

            BankAccountBalance current = balanceRepository
                    .findById(existing.getAcctId())
                    .orElseThrow();

            return toResponse(existing, current);
        }

        BankAccount account = accountRepository
                .findByIdForUpdate(acctId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Account not found")
                );

        if (!"ACTIVE".equals(account.getAcctSts())) {
            throw new IllegalArgumentException(
                    "Only ACTIVE accounts can perform transactions"
            );
        }

        if (!"MYR".equals(account.getCcyCd())) {
            throw new IllegalArgumentException(
                    "Only MYR accounts are supported"
            );
        }

        BankAccountBalance balance = balanceRepository
                .findByIdForUpdate(acctId)
                .orElseThrow(() ->
                        new IllegalStateException("Account balance missing")
                );

        BigDecimal newLedger;
        BigDecimal newAvailable;

        if ("DEPOSIT".equals(type)) {
            newLedger = balance.getLedgerBal().add(amount);
            newAvailable = balance.getAvailBal().add(amount);
        } else {
            if (balance.getAvailBal().compareTo(amount) < 0) {
                throw new IllegalArgumentException("Insufficient funds");
            }

            newLedger = balance.getLedgerBal().subtract(amount);
            newAvailable = balance.getAvailBal().subtract(amount);
        }

        OffsetDateTime now = OffsetDateTime.now();

        BankLedgerAccount cashGl = glRepository.findByGlCd("100100")
                .orElseThrow(() ->
                        new IllegalStateException("Cash GL missing")
                );

        BankLedgerAccount depositGl = glRepository.findByGlCd("200100")
                .orElseThrow(() ->
                        new IllegalStateException("Deposit GL missing")
                );

        if (!Boolean.TRUE.equals(cashGl.getActFlg()) ||
                !Boolean.TRUE.equals(depositGl.getActFlg())) {
            throw new IllegalStateException("Required GL is inactive");
        }

        BankTransaction txn = new BankTransaction();
        txn.setTxnRef("TXN-" + UUID.randomUUID());
        txn.setAcctId(acctId);
        txn.setTxnTyp(type);
        txn.setTxnAmt(amount);
        txn.setCcyCd("MYR");
        txn.setTxnSts("POSTED");
        txn.setIdempotencyKey(idempotencyKey);
        txn.setCrtDt(now);
        txn.setCrtBy(authentication.getName());

        txn = transactionRepository.saveAndFlush(txn);

        boolean deposit = "DEPOSIT".equals(type);

        BankLedgerEntry debit = new BankLedgerEntry();
        debit.setTxnId(txn.getTxnId());
        debit.setGlId(deposit ? cashGl.getGlId() : depositGl.getGlId());
        debit.setAcctId(deposit ? null : acctId);
        debit.setEntrySide("DEBIT");
        debit.setEntryAmt(amount);
        debit.setCrtDt(now);

        BankLedgerEntry credit = new BankLedgerEntry();
        credit.setTxnId(txn.getTxnId());
        credit.setGlId(deposit ? depositGl.getGlId() : cashGl.getGlId());
        credit.setAcctId(deposit ? acctId : null);
        credit.setEntrySide("CREDIT");
        credit.setEntryAmt(amount);
        credit.setCrtDt(now);

        entryRepository.save(debit);
        entryRepository.save(credit);

        balance.setLedgerBal(newLedger);
        balance.setAvailBal(newAvailable);
        balance.setUpdDt(now);
        balance.setUpdBy(authentication.getName());

        balanceRepository.saveAndFlush(balance);

        return toResponse(txn, balance);
    }

    private BankPostingResponse toResponse(
            BankTransaction txn,
            BankAccountBalance balance
    ) {
        return new BankPostingResponse(
                txn.getTxnId(),
                txn.getTxnRef(),
                txn.getAcctId(),
                txn.getTxnTyp(),
                txn.getTxnAmt(),
                txn.getCcyCd(),
                txn.getTxnSts(),
                balance.getLedgerBal(),
                balance.getAvailBal(),
                txn.getCrtDt()
        );
    }
}
