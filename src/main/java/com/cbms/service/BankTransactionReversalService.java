
package com.cbms.service;

import com.cbms.dto.TransactionReversalRequest;
import com.cbms.dto.TransactionReversalResponse;
import com.cbms.entity.*;
import com.cbms.repository.*;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class BankTransactionReversalService {

    private final BankTransactionRepository transactionRepository;
    private final BankAccountRepository accountRepository;
    private final BankAccountBalanceRepository balanceRepository;
    private final BankLedgerAccountRepository glRepository;
    private final BankLedgerEntryRepository entryRepository;
    private final BankTransactionReversalAuditRepository auditRepository;
    private final IdempotencyLockRepository idempotencyLockRepository;

    public BankTransactionReversalService(
            BankTransactionRepository transactionRepository,
            BankAccountRepository accountRepository,
            BankAccountBalanceRepository balanceRepository,
            BankLedgerAccountRepository glRepository,
            BankLedgerEntryRepository entryRepository,
            BankTransactionReversalAuditRepository auditRepository,
            IdempotencyLockRepository idempotencyLockRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.balanceRepository = balanceRepository;
        this.glRepository = glRepository;
        this.entryRepository = entryRepository;
        this.auditRepository = auditRepository;
        this.idempotencyLockRepository = idempotencyLockRepository;
    }

    @Transactional
    public TransactionReversalResponse reverse(
            Long originalTxnId,
            TransactionReversalRequest request,
            String idempotencyKey,
            Authentication authentication
    ) {
        if (idempotencyKey == null ||
                idempotencyKey.isBlank() ||
                idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "Valid Idempotency-Key is required"
            );
        }

        if (request == null ||
                request.reason() == null ||
                request.reason().isBlank() ||
                request.reason().length() > 250) {
            throw new IllegalArgumentException(
                    "A reversal reason is required"
            );
        }

        idempotencyLockRepository.lock(idempotencyKey);
        idempotencyLockRepository.lockReversal(originalTxnId);

        BankTransaction existing = transactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElse(null);

        if (existing != null) {

            if (!"REVERSAL".equals(existing.getTxnTyp()) ||
                    !originalTxnId.equals(existing.getReversalOfTxnId())) {
                throw new IllegalArgumentException(
                        "Idempotency key already used for another request"
                );
            }

            BankTransactionReversalAudit existingAudit = auditRepository
                    .findByReversalTxnId(existing.getTxnId())
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Reversal audit record missing"
                            )
                    );

            if (!existingAudit.getReason().equals(request.reason().trim())) {
                throw new IllegalArgumentException(
                        "Idempotency key reused with a different reversal reason"
                );
            }

            return buildResponse(existing);
        }

        BankTransaction original = transactionRepository
                .findById(originalTxnId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Original transaction not found"
                        )
                );

        if (!"POSTED".equals(original.getTxnSts()) ||
                !(
                        "DEPOSIT".equals(original.getTxnTyp()) ||
                                "WITHDRAWAL".equals(original.getTxnTyp()) ||
                                "TRANSFER".equals(original.getTxnTyp())
                )) {
            throw new IllegalArgumentException(
                    "Transaction cannot be reversed"
            );
        }

        Long sourceId = original.getAcctId();
        Long destinationId = original.getDestAcctId();

        // Always lock affected accounts in ascending ID order.
        Long firstId = destinationId == null
                ? sourceId
                : Math.min(sourceId, destinationId);

        Long secondId = destinationId == null
                ? null
                : Math.max(sourceId, destinationId);

        BankAccount firstAccount = accountRepository
                .findByIdForUpdate(firstId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Account not found: " + firstId
                        )
                );

        BankAccount secondAccount = secondId == null
                ? null
                : accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Account not found: " + secondId
                        )
                );

        if (!"ACTIVE".equals(firstAccount.getAcctSts()) ||
                (secondAccount != null &&
                        !"ACTIVE".equals(secondAccount.getAcctSts()))) {
            throw new IllegalArgumentException(
                    "Affected accounts must be ACTIVE"
            );
        }

        if (!"MYR".equals(firstAccount.getCcyCd()) ||
                (secondAccount != null &&
                        !"MYR".equals(secondAccount.getCcyCd()))) {
            throw new IllegalArgumentException(
                    "Only MYR reversals are supported"
            );
        }

        if (transactionRepository.existsByReversalOfTxnId(originalTxnId)) {
            throw new IllegalArgumentException(
                    "Transaction already reversed"
            );
        }

        BankAccountBalance firstBalance = balanceRepository
                .findByIdForUpdate(firstId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Account balance missing"
                        )
                );

        BankAccountBalance secondBalance = secondId == null
                ? null
                : balanceRepository.findByIdForUpdate(secondId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Account balance missing"
                        )
                );

        BankAccountBalance sourceBalance =
                sourceId.equals(firstId)
                        ? firstBalance : secondBalance;

        BankAccountBalance destinationBalance =
                destinationId == null
                        ? null
                        : destinationId.equals(firstId)
                        ? firstBalance : secondBalance;

        BigDecimal amount = original.getTxnAmt();
        OffsetDateTime now = OffsetDateTime.now();

        BankLedgerAccount cashGl = glRepository
                .findByGlCd("100100")
                .orElseThrow(() ->
                        new IllegalStateException("Cash GL missing")
                );

        BankLedgerAccount depositGl = glRepository
                .findByGlCd("200100")
                .orElseThrow(() ->
                        new IllegalStateException("Deposit GL missing")
                );

        if (!Boolean.TRUE.equals(cashGl.getActFlg()) ||
                !Boolean.TRUE.equals(depositGl.getActFlg()) ||
                !"MYR".equals(cashGl.getCcyCd()) ||
                !"MYR".equals(depositGl.getCcyCd())) {
            throw new IllegalStateException(
                    "Required GL account is inactive or invalid"
            );
        }

        Long reversalSourceId;
        Long reversalDestinationId = null;

        if ("DEPOSIT".equals(original.getTxnTyp())) {

            ensureSufficientFunds(sourceBalance, amount);

            decreaseBalance(sourceBalance, amount, now,
                    authentication.getName());

            reversalSourceId = sourceId;

        } else if ("WITHDRAWAL".equals(original.getTxnTyp())) {

            increaseBalance(sourceBalance, amount, now,
                    authentication.getName());

            reversalSourceId = sourceId;

        } else {

            // Reverse original A -> B by moving money B -> A.
            ensureSufficientFunds(destinationBalance, amount);

            decreaseBalance(destinationBalance, amount, now,
                    authentication.getName());

            increaseBalance(sourceBalance, amount, now,
                    authentication.getName());

            reversalSourceId = destinationId;
            reversalDestinationId = sourceId;
        }

        BankTransaction reversal = new BankTransaction();
        reversal.setTxnRef("REV-" + UUID.randomUUID());
        reversal.setAcctId(reversalSourceId);
        reversal.setDestAcctId(reversalDestinationId);
        reversal.setTxnTyp("REVERSAL");
        reversal.setTxnAmt(amount);
        reversal.setCcyCd("MYR");
        reversal.setTxnSts("POSTED");
        reversal.setIdempotencyKey(idempotencyKey);
        reversal.setReversalOfTxnId(originalTxnId);
        reversal.setCrtDt(now);
        reversal.setCrtBy(authentication.getName());

        reversal = transactionRepository.saveAndFlush(reversal);

        if ("DEPOSIT".equals(original.getTxnTyp())) {

            saveEntry(reversal.getTxnId(), depositGl.getGlId(),
                    sourceId, "DEBIT", amount, now);

            saveEntry(reversal.getTxnId(), cashGl.getGlId(),
                    null, "CREDIT", amount, now);

        } else if ("WITHDRAWAL".equals(original.getTxnTyp())) {

            saveEntry(reversal.getTxnId(), cashGl.getGlId(),
                    null, "DEBIT", amount, now);

            saveEntry(reversal.getTxnId(), depositGl.getGlId(),
                    sourceId, "CREDIT", amount, now);

        } else {

            saveEntry(reversal.getTxnId(), depositGl.getGlId(),
                    destinationId, "DEBIT", amount, now);

            saveEntry(reversal.getTxnId(), depositGl.getGlId(),
                    sourceId, "CREDIT", amount, now);
        }

        balanceRepository.save(sourceBalance);

        if (destinationBalance != null) {
            balanceRepository.save(destinationBalance);
        }

        BankTransactionReversalAudit audit =
                new BankTransactionReversalAudit();

        audit.setOriginalTxnId(originalTxnId);
        audit.setReversalTxnId(reversal.getTxnId());
        audit.setReason(request.reason().trim());
        audit.setReversedBy(authentication.getName());
        audit.setReversedDt(now);

        auditRepository.save(audit);

        entryRepository.flush();
        balanceRepository.flush();
        auditRepository.flush();

        return buildResponse(reversal);
    }

    private void ensureSufficientFunds(
            BankAccountBalance balance,
            BigDecimal amount
    ) {
        if (balance.getAvailBal().compareTo(amount) < 0 ||
                balance.getLedgerBal().compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient funds for reversal"
            );
        }
    }

    private void increaseBalance(
            BankAccountBalance balance,
            BigDecimal amount,
            OffsetDateTime now,
            String username
    ) {
        balance.setLedgerBal(balance.getLedgerBal().add(amount));
        balance.setAvailBal(balance.getAvailBal().add(amount));
        balance.setUpdDt(now);
        balance.setUpdBy(username);
    }

    private void decreaseBalance(
            BankAccountBalance balance,
            BigDecimal amount,
            OffsetDateTime now,
            String username
    ) {
        balance.setLedgerBal(balance.getLedgerBal().subtract(amount));
        balance.setAvailBal(balance.getAvailBal().subtract(amount));
        balance.setUpdDt(now);
        balance.setUpdBy(username);
    }

    private void saveEntry(
            Long txnId,
            Long glId,
            Long acctId,
            String side,
            BigDecimal amount,
            OffsetDateTime now
    ) {
        BankLedgerEntry entry = new BankLedgerEntry();
        entry.setTxnId(txnId);
        entry.setGlId(glId);
        entry.setAcctId(acctId);
        entry.setEntrySide(side);
        entry.setEntryAmt(amount);
        entry.setCrtDt(now);

        entryRepository.save(entry);
    }

    private TransactionReversalResponse buildResponse(
            BankTransaction reversal
    ) {
        BankTransaction original = transactionRepository
                .findById(reversal.getReversalOfTxnId())
                .orElseThrow();

        BankAccountBalance sourceBalance = balanceRepository
                .findById(original.getAcctId())
                .orElseThrow();

        BankAccountBalance destinationBalance =
                original.getDestAcctId() == null
                        ? null
                        : balanceRepository
                        .findById(original.getDestAcctId())
                        .orElseThrow();

        return new TransactionReversalResponse(
                reversal.getTxnId(),
                reversal.getTxnRef(),
                original.getTxnId(),
                original.getTxnTyp(),
                original.getAcctId(),
                original.getDestAcctId(),
                reversal.getTxnAmt(),
                reversal.getTxnSts(),
                sourceBalance.getLedgerBal(),
                destinationBalance == null
                        ? null
                        : destinationBalance.getLedgerBal(),
                reversal.getCrtDt()
        );
    }
}
