package com.cbms.service;

import com.cbms.entity.BankAccount;
import com.cbms.entity.BankAccountBalance;
import com.cbms.entity.BankTransaction;
import com.cbms.entity.BankLedgerAccount;
import com.cbms.entity.BankLedgerEntry;

import com.cbms.repository.BankAccountRepository;
import com.cbms.repository.BankAccountBalanceRepository;
import com.cbms.repository.BankTransactionRepository;
import com.cbms.repository.BankLedgerAccountRepository;
import com.cbms.repository.BankLedgerEntryRepository;
import com.cbms.repository.IdempotencyLockRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class BankFixedDepositFundingAdapter
        implements BankFixedDepositFundingPort {

    private static final String CUSTOMER_DEPOSIT_GL = "200100";
    private static final String FIXED_DEPOSIT_GL = "200200";

    private final BankAccountRepository accountRepository;
    private final BankAccountBalanceRepository balanceRepository;
    private final BankTransactionRepository transactionRepository;
    private final BankLedgerAccountRepository glRepository;
    private final BankLedgerEntryRepository entryRepository;
    private final IdempotencyLockRepository idempotencyLockRepository;

    public BankFixedDepositFundingAdapter(
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

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String debitForPlacement(
            Long sourceAccountId,
            Long customerId,
            BigDecimal amount,
            String placementReference,
            String username
    ) {

        if (sourceAccountId == null || customerId == null) {
            throw new IllegalArgumentException(
                    "Source account and customer are required"
            );
        }

        if (amount == null
                || amount.signum() <= 0
                || amount.scale() > 2
                || amount.precision() - amount.scale() > 17) {
            throw new IllegalArgumentException(
                    "Invalid placement amount"
            );
        }

        if (placementReference == null
                || placementReference.isBlank()
                || placementReference.length() > 50) {
            throw new IllegalArgumentException(
                    "Invalid placement reference"
            );
        }

        // Namespaced to avoid collisions with transfer keys.
        String idempotencyKey = "FD:" + placementReference;

        if (idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency key exceeds 100 characters"
            );
        }

        // Serialize requests sharing the same placement reference.
        idempotencyLockRepository.lock(idempotencyKey);

        if (transactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .isPresent()) {
            throw new IllegalArgumentException(
                    "Placement reference already used"
            );
        }

        // Lock source account.
        BankAccount sourceAccount = accountRepository
                .findByIdForUpdate(sourceAccountId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Source account not found"
                        )
                );

        // Ownership validation.
        if (!customerId.equals(sourceAccount.getCustId())) {
            throw new IllegalArgumentException(
                    "Source account does not belong to FD customer"
            );
        }

        if (!"ACTIVE".equals(sourceAccount.getAcctSts())) {
            throw new IllegalArgumentException(
                    "Source account is not ACTIVE"
            );
        }

        if (!"MYR".equals(sourceAccount.getCcyCd())) {
            throw new IllegalArgumentException(
                    "Only MYR funding is supported"
            );
        }

        // Only demand deposit accounts may fund an FD.
        if (!"SAVINGS".equals(sourceAccount.getAcctTyp())
                && !"CURRENT".equals(sourceAccount.getAcctTyp())) {
            throw new IllegalArgumentException(
                    "Funding requires a SAVINGS or CURRENT account"
            );
        }

        // Lock balance row.
        BankAccountBalance sourceBalance = balanceRepository
                .findByIdForUpdate(sourceAccountId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Source account balance missing"
                        )
                );

        if (sourceBalance.getAvailBal().compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient available balance"
            );
        }

        if (sourceBalance.getLedgerBal().compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient ledger balance"
            );
        }

        BankLedgerAccount customerDepositGl = glRepository
                .findByGlCd(CUSTOMER_DEPOSIT_GL)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Customer Deposits GL 200100 missing"
                        )
                );

        BankLedgerAccount fixedDepositGl = glRepository
                .findByGlCd(FIXED_DEPOSIT_GL)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Fixed Deposit GL 200200 missing"
                        )
                );

        validateGl(customerDepositGl, CUSTOMER_DEPOSIT_GL);
        validateGl(fixedDepositGl, FIXED_DEPOSIT_GL);

        OffsetDateTime now = OffsetDateTime.now();

        // Create transaction.
        BankTransaction txn = new BankTransaction();
        txn.setTxnRef("TXN-" + UUID.randomUUID());
        txn.setAcctId(sourceAccountId);
        txn.setDestAcctId(null);
        txn.setTxnTyp("FD_PLACEMENT");
        txn.setTxnAmt(amount);
        txn.setCcyCd("MYR");
        txn.setTxnSts("POSTED");
        txn.setIdempotencyKey(idempotencyKey);
        txn.setCrtDt(now);
        txn.setCrtBy(username);

        txn = transactionRepository.saveAndFlush(txn);

        // Debit customer deposits liability.
        BankLedgerEntry debit = new BankLedgerEntry();
        debit.setTxnId(txn.getTxnId());
        debit.setGlId(customerDepositGl.getGlId());
        debit.setAcctId(sourceAccountId);
        debit.setEntrySide("DEBIT");
        debit.setEntryAmt(amount);
        debit.setCrtDt(now);

        // Credit fixed deposit liability.
        // No ordinary bank account ID is associated with this entry.
        BankLedgerEntry credit = new BankLedgerEntry();
        credit.setTxnId(txn.getTxnId());
        credit.setGlId(fixedDepositGl.getGlId());
        credit.setAcctId(null);
        credit.setEntrySide("CREDIT");
        credit.setEntryAmt(amount);
        credit.setCrtDt(now);

        entryRepository.save(debit);
        entryRepository.save(credit);

        // Deduct source account balance.
        sourceBalance.setLedgerBal(
                sourceBalance.getLedgerBal().subtract(amount)
        );

        sourceBalance.setAvailBal(
                sourceBalance.getAvailBal().subtract(amount)
        );

        sourceBalance.setUpdDt(now);
        sourceBalance.setUpdBy(username);

        balanceRepository.save(sourceBalance);

        // Force constraint failures inside the current transaction.
        entryRepository.flush();
        balanceRepository.flush();

        return txn.getTxnRef();
    }

    private void validateGl(
            BankLedgerAccount gl,
            String glCode
    ) {
        if (!Boolean.TRUE.equals(gl.getActFlg())
                || !"MYR".equals(gl.getCcyCd())) {
            throw new IllegalStateException(
                    "GL " + glCode + " is inactive or invalid"
            );
        }
    }
}
