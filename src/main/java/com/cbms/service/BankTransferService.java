package com.cbms.service;

import com.cbms.dto.TransferRequest;
import com.cbms.dto.TransferResponse;
import com.cbms.entity.*;
import com.cbms.repository.*;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class BankTransferService {

    private final BankAccountRepository accountRepository;
    private final BankAccountBalanceRepository balanceRepository;
    private final BankTransactionRepository transactionRepository;
    private final BankLedgerAccountRepository glRepository;
    private final BankLedgerEntryRepository entryRepository;
    private final IdempotencyLockRepository idempotencyLockRepository;

    public BankTransferService(
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
    public TransferResponse transfer(
            Long sourceAccountId,
            TransferRequest request,
            String idempotencyKey,
            Authentication authentication
    ) {
        Long destinationAccountId = request.destinationAccountId();
        BigDecimal amount = request.amount();

        if (sourceAccountId == null ||
                destinationAccountId == null ||
                sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException(
                    "Source and destination accounts must be different"
            );
        }

        if (idempotencyKey == null ||
                idempotencyKey.isBlank() ||
                idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "A valid Idempotency-Key header is required"
            );
        }

        if (amount == null ||
                amount.signum() <= 0 ||
                amount.scale() > 2 ||
                amount.precision() - amount.scale() > 17) {
            throw new IllegalArgumentException(
                    "Invalid transfer amount"
            );
        }

        // Serialize all requests using the same idempotency key.
        idempotencyLockRepository.lock(idempotencyKey);

        BankTransaction existing = transactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElse(null);

        if (existing != null) {
            if (!"TRANSFER".equals(existing.getTxnTyp()) ||
                    !existing.getAcctId().equals(sourceAccountId) ||
                    !destinationAccountId.equals(existing.getDestAcctId()) ||
                    existing.getTxnAmt().compareTo(amount) != 0) {
                throw new IllegalArgumentException(
                        "Idempotency key already used for a different request"
                );
            }

            BankAccountBalance sourceBalance = balanceRepository
                    .findById(sourceAccountId)
                    .orElseThrow();

            BankAccountBalance destinationBalance = balanceRepository
                    .findById(destinationAccountId)
                    .orElseThrow();

            return toResponse(
                    existing,
                    sourceBalance,
                    destinationBalance
            );
        }

        // Lock both accounts in a consistent order to avoid deadlocks.
        Long firstId = Math.min(sourceAccountId, destinationAccountId);
        Long secondId = Math.max(sourceAccountId, destinationAccountId);

        BankAccount firstAccount = accountRepository
                .findByIdForUpdate(firstId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Bank account not found: " + firstId
                        )
                );

        BankAccount secondAccount = accountRepository
                .findByIdForUpdate(secondId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Bank account not found: " + secondId
                        )
                );

        BankAccount sourceAccount =
                sourceAccountId.equals(firstId)
                        ? firstAccount : secondAccount;

        BankAccount destinationAccount =
                destinationAccountId.equals(firstId)
                        ? firstAccount : secondAccount;

        if (!"ACTIVE".equals(sourceAccount.getAcctSts()) ||
                !"ACTIVE".equals(destinationAccount.getAcctSts())) {
            throw new IllegalArgumentException(
                    "Both accounts must be ACTIVE"
            );
        }

        if (!"MYR".equals(sourceAccount.getCcyCd()) ||
                !"MYR".equals(destinationAccount.getCcyCd())) {
            throw new IllegalArgumentException(
                    "Only MYR transfers are supported"
            );
        }

        BankAccountBalance firstBalance = balanceRepository
                .findByIdForUpdate(firstId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Balance record missing: " + firstId
                        )
                );

        BankAccountBalance secondBalance = balanceRepository
                .findByIdForUpdate(secondId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Balance record missing: " + secondId
                        )
                );

        BankAccountBalance sourceBalance =
                sourceAccountId.equals(firstId)
                        ? firstBalance : secondBalance;

        BankAccountBalance destinationBalance =
                destinationAccountId.equals(firstId)
                        ? firstBalance : secondBalance;

        if (sourceBalance.getAvailBal().compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient funds"
            );
        }

        BigDecimal sourceLedger = sourceBalance
                .getLedgerBal()
                .subtract(amount);

        BigDecimal sourceAvailable = sourceBalance
                .getAvailBal()
                .subtract(amount);

        BigDecimal destinationLedger = destinationBalance
                .getLedgerBal()
                .add(amount);

        BigDecimal destinationAvailable = destinationBalance
                .getAvailBal()
                .add(amount);

        BankLedgerAccount depositGl = glRepository
                .findByGlCd("200100")
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Customer Deposits GL missing"
                        )
                );

        if (!Boolean.TRUE.equals(depositGl.getActFlg()) ||
                !"MYR".equals(depositGl.getCcyCd())) {
            throw new IllegalStateException(
                    "Customer Deposits GL is inactive or invalid"
            );
        }

        OffsetDateTime now = OffsetDateTime.now();

        BankTransaction txn = new BankTransaction();
        txn.setTxnRef("TXN-" + UUID.randomUUID());
        txn.setAcctId(sourceAccountId);
        txn.setDestAcctId(destinationAccountId);
        txn.setTxnTyp("TRANSFER");
        txn.setTxnAmt(amount);
        txn.setCcyCd("MYR");
        txn.setTxnSts("POSTED");
        txn.setIdempotencyKey(idempotencyKey);
        txn.setCrtDt(now);
        txn.setCrtBy(authentication.getName());

        txn = transactionRepository.saveAndFlush(txn);

        BankLedgerEntry debit = new BankLedgerEntry();
        debit.setTxnId(txn.getTxnId());
        debit.setGlId(depositGl.getGlId());
        debit.setAcctId(sourceAccountId);
        debit.setEntrySide("DEBIT");
        debit.setEntryAmt(amount);
        debit.setCrtDt(now);

        BankLedgerEntry credit = new BankLedgerEntry();
        credit.setTxnId(txn.getTxnId());
        credit.setGlId(depositGl.getGlId());
        credit.setAcctId(destinationAccountId);
        credit.setEntrySide("CREDIT");
        credit.setEntryAmt(amount);
        credit.setCrtDt(now);

        entryRepository.save(debit);
        entryRepository.save(credit);

        sourceBalance.setLedgerBal(sourceLedger);
        sourceBalance.setAvailBal(sourceAvailable);
        sourceBalance.setUpdDt(now);
        sourceBalance.setUpdBy(authentication.getName());

        destinationBalance.setLedgerBal(destinationLedger);
        destinationBalance.setAvailBal(destinationAvailable);
        destinationBalance.setUpdDt(now);
        destinationBalance.setUpdBy(authentication.getName());

        balanceRepository.save(sourceBalance);
        balanceRepository.save(destinationBalance);

        // Flush before returning to catch DB constraint failures
        // within this transaction.
        balanceRepository.flush();
        entryRepository.flush();

        return toResponse(
                txn,
                sourceBalance,
                destinationBalance
        );
    }

    private TransferResponse toResponse(
            BankTransaction txn,
            BankAccountBalance sourceBalance,
            BankAccountBalance destinationBalance
    ) {
        return new TransferResponse(
                txn.getTxnId(),
                txn.getTxnRef(),
                txn.getAcctId(),
                txn.getDestAcctId(),
                txn.getTxnAmt(),
                txn.getCcyCd(),
                txn.getTxnSts(),
                sourceBalance.getLedgerBal(),
                destinationBalance.getLedgerBal(),
                txn.getCrtDt()
        );
    }
}
