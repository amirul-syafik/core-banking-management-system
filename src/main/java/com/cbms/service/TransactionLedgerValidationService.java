package com.cbms.service;

import com.cbms.entity.BankLedgerAccount;
import com.cbms.entity.BankLedgerEntry;
import com.cbms.entity.BankTransaction;
import com.cbms.repository.BankLedgerAccountRepository;
import com.cbms.repository.BankLedgerEntryRepository;
import com.cbms.repository.BankTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only, transaction-level integrity checker for ordinary MYR postings.
 * This is a service API, not an HTTP endpoint or an automatic posting blocker.
 * It does not reconcile stored balances or assert that every GL in CBMS uses
 * these two-entry rules; other transaction families require their own rules.
 */
@Service
public class TransactionLedgerValidationService {

    private final BankTransactionRepository transactions;
    private final BankLedgerEntryRepository entries;
    private final BankLedgerAccountRepository glAccounts;

    public TransactionLedgerValidationService(BankTransactionRepository transactions,
                                              BankLedgerEntryRepository entries,
                                              BankLedgerAccountRepository glAccounts) {
        this.transactions = transactions;
        this.entries = entries;
        this.glAccounts = glAccounts;
    }

    public record ValidationResult(Long transactionId, boolean valid, List<String> findings) {
        public ValidationResult {
            findings = List.copyOf(findings);
        }
    }

    private record ExpectedEntry(Long glId, Long accountId, String side, BigDecimal amount) { }

    @Transactional(readOnly = true)
    public ValidationResult validate(Long transactionId) {
        if (transactionId == null || transactionId <= 0) {
            throw new IllegalArgumentException("A valid transaction ID is required");
        }
        BankTransaction txn = transactions.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        List<String> findings = new ArrayList<>();
        if (!"POSTED".equals(txn.getTxnSts())) findings.add("Transaction status is not POSTED");
        if (!"MYR".equals(txn.getCcyCd())) findings.add("Unsupported transaction currency");
        if (txn.getTxnAmt() == null || txn.getTxnAmt().signum() <= 0)
            findings.add("Transaction amount must be positive");
        List<ExpectedEntry> expected = new ArrayList<>();
        if (findings.isEmpty()) {
            Long depositGl = glAccounts.findByGlCd("200100")
                    .map(BankLedgerAccount::getGlId).orElse(null);
            Long cashGl = glAccounts.findByGlCd("100100")
                    .map(BankLedgerAccount::getGlId).orElse(null);
            if (depositGl == null || cashGl == null) {
                findings.add("Required GL account not found");
            } else {
                Long src = txn.getAcctId();
                Long dst = txn.getDestAcctId();
                BigDecimal amount = txn.getTxnAmt();
                switch (txn.getTxnTyp()) {
                    case "DEPOSIT" -> {
                        if (dst != null || txn.getReversalOfTxnId() != null) findings.add("Unexpected deposit links");
                        expected.add(new ExpectedEntry(cashGl, null, "DEBIT", amount));
                        expected.add(new ExpectedEntry(depositGl, src, "CREDIT", amount));
                    }
                    case "WITHDRAWAL" -> {
                        if (dst != null || txn.getReversalOfTxnId() != null) findings.add("Unexpected withdrawal links");
                        expected.add(new ExpectedEntry(depositGl, src, "DEBIT", amount));
                        expected.add(new ExpectedEntry(cashGl, null, "CREDIT", amount));
                    }
                    case "TRANSFER" -> {
                        if (dst == null || Objects.equals(src, dst) || txn.getReversalOfTxnId() != null)
                            findings.add("Invalid transfer account links");
                        expected.add(new ExpectedEntry(depositGl, src, "DEBIT", amount));
                        expected.add(new ExpectedEntry(depositGl, dst, "CREDIT", amount));
                    }
                    case "REVERSAL" -> {
                        if (txn.getReversalOfTxnId() == null) {
                            findings.add("Missing original transaction link");
                        } else {
                            BankTransaction original = transactions.findById(txn.getReversalOfTxnId()).orElse(null);
                            if (original == null) {
                                findings.add("Original transaction not found");
                            } else {
                                if (!Objects.equals(amount, original.getTxnAmt()) &&
                                    (original.getTxnAmt() == null || amount.compareTo(original.getTxnAmt()) != 0))
                                    findings.add("Reversal amount differs from original");
                                if (!"POSTED".equals(original.getTxnSts()) || !"MYR".equals(original.getCcyCd()))
                                    findings.add("Original transaction has unsupported status or currency");
                                switch (original.getTxnTyp()) {
                                    case "DEPOSIT" -> {
                                        if (!Objects.equals(src, original.getAcctId()) || dst != null)
                                            findings.add("Reversal account links differ from original deposit");
                                        expected.add(new ExpectedEntry(depositGl, original.getAcctId(), "DEBIT", amount));
                                        expected.add(new ExpectedEntry(cashGl, null, "CREDIT", amount));
                                    }
                                    case "WITHDRAWAL" -> {
                                        if (!Objects.equals(src, original.getAcctId()) || dst != null)
                                            findings.add("Reversal account links differ from original withdrawal");
                                        expected.add(new ExpectedEntry(cashGl, null, "DEBIT", amount));
                                        expected.add(new ExpectedEntry(depositGl, original.getAcctId(), "CREDIT", amount));
                                    }
                                    case "TRANSFER" -> {
                                        if (original.getDestAcctId() == null ||
                                            !Objects.equals(src, original.getDestAcctId()) ||
                                            !Objects.equals(dst, original.getAcctId()))
                                            findings.add("Reversal direction differs from original transfer");
                                        expected.add(new ExpectedEntry(depositGl, original.getDestAcctId(), "DEBIT", amount));
                                        expected.add(new ExpectedEntry(depositGl, original.getAcctId(), "CREDIT", amount));
                                    }
                                    default -> findings.add("Unsupported original transaction type");
                                }
                            }
                        }
                    }
                    default -> findings.add("Unsupported transaction type");
                }
            }
        }
        List<BankLedgerEntry> actual = entries.findByTxnIdOrderByEntryIdAsc(transactionId);
        if (actual.size() != 2) findings.add("Expected exactly two ledger entries, found " + actual.size());
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (BankLedgerEntry entry : actual) {
            if (entry.getEntryAmt() == null || entry.getEntryAmt().signum() <= 0) {
                findings.add("Non-positive or missing ledger amount");
                continue;
            }
            if ("DEBIT".equals(entry.getEntrySide())) debit = debit.add(entry.getEntryAmt());
            else if ("CREDIT".equals(entry.getEntrySide())) credit = credit.add(entry.getEntryAmt());
            else findings.add("Invalid ledger entry side");
        }
        if (debit.compareTo(credit) != 0) findings.add("Debit and credit totals differ");
        if (expected.size() == 2) {
            List<BankLedgerEntry> remaining = new ArrayList<>(actual);
            for (ExpectedEntry e : expected) {
                boolean found = false;
                for (int i = 0; i < remaining.size(); i++) {
                    BankLedgerEntry a = remaining.get(i);
                    if (Objects.equals(e.glId(), a.getGlId()) &&
                        Objects.equals(e.accountId(), a.getAcctId()) &&
                        Objects.equals(e.side(), a.getEntrySide()) &&
                        a.getEntryAmt() != null && e.amount().compareTo(a.getEntryAmt()) == 0) {
                        remaining.remove(i);
                        found = true;
                        break;
                    }
                }
                if (!found) findings.add("Missing or mismatched expected " + e.side() + " entry");
            }
            if (!remaining.isEmpty()) findings.add("Unexpected or duplicate ledger entries");
        }
        return new ValidationResult(transactionId, findings.isEmpty(), findings);
    }
}
