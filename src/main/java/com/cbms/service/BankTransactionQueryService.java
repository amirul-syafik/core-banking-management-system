package com.cbms.service;

import com.cbms.dto.TransactionDetailResponse;
import com.cbms.dto.TransactionHistoryResponse;
import com.cbms.dto.TransactionLedgerEntryResponse;

import com.cbms.entity.BankTransaction;
import com.cbms.entity.BankTransactionType;
import com.cbms.entity.BankLedgerEntry;
import com.cbms.entity.BankLedgerAccount;

import com.cbms.repository.BankAccountRepository;
import com.cbms.repository.BankTransactionRepository;
import com.cbms.repository.BankLedgerEntryRepository;
import com.cbms.repository.BankLedgerAccountRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class BankTransactionQueryService {

    private final BankAccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final BankLedgerEntryRepository entryRepository;
    private final BankLedgerAccountRepository glRepository;

    public BankTransactionQueryService(
            BankAccountRepository accountRepository,
            BankTransactionRepository transactionRepository,
            BankLedgerEntryRepository entryRepository,
            BankLedgerAccountRepository glRepository
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.glRepository = glRepository;
    }

    @Transactional(readOnly = true)
    public Page<TransactionHistoryResponse> getAccountTransactions(
            Long acctId,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Invalid pagination parameters"
            );
        }

        if (!accountRepository.existsById(acctId)) {
            throw new IllegalArgumentException(
                    "Bank account not found: " + acctId
            );
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("crtDt"),
                        Sort.Order.desc("txnId")
                )
        );


        Page<BankTransaction> transactions = transactionRepository
                .findByAcctIdOrDestAcctId(acctId, acctId, pageable);

        Set<Long> originalTxnIds = transactions.getContent()
                .stream()
                .filter(t -> "REVERSAL".equals(t.getTxnTyp()))
                .map(BankTransaction::getReversalOfTxnId)
                .collect(Collectors.toSet());

        Map<Long, BankTransaction> originals = transactionRepository
                .findAllById(originalTxnIds)
                .stream()
                .collect(Collectors.toMap(
                        BankTransaction::getTxnId,
                        Function.identity()
                ));

        return transactions.map(transaction ->
                toHistoryResponse(transaction, acctId, originals)
        );
    }

    @Transactional(readOnly = true)
    public Page<TransactionHistoryResponse> inquireAccountTransactions(Long acctId, String reference,
                                                                       String type, String status, LocalDate from, LocalDate to, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination parameters");
        if (!accountRepository.existsById(acctId)) throw new IllegalArgumentException("Bank account not found");
        if (from != null && to != null && from.isAfter(to))
            throw new IllegalArgumentException("From date must not exceed To date");
        OffsetDateTime start = (from == null ? LocalDate.of(1900, 1, 1) : from).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = (to == null ? LocalDate.of(9999, 12, 30) : to.plusDays(1)).atStartOfDay().atOffset(ZoneOffset.UTC);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("crtDt"), Sort.Order.desc("txnId")));
        Page<BankTransaction> transactions = transactionRepository.inquireByAccount(acctId,
                reference == null ? "" : reference.trim(), type == null ? "" : type.trim(),
                status == null ? "" : status.trim(), start, end, pageable);
        Set<Long> originalIds = transactions.getContent().stream()
                .filter(t -> "REVERSAL".equals(t.getTxnTyp()))
                .map(BankTransaction::getReversalOfTxnId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, BankTransaction> originals = transactionRepository.findAllById(originalIds).stream()
                .collect(Collectors.toMap(BankTransaction::getTxnId, Function.identity()));
        return transactions.map(t -> toHistoryResponse(t, acctId, originals));
    }

    @Transactional(readOnly = true)
    public TransactionDetailResponse getTransactionDetails(Long txnId) {

        BankTransaction transaction = transactionRepository
                .findById(txnId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Transaction not found: " + txnId
                        )
                );

        List<BankLedgerEntry> entries = entryRepository
                .findByTxnIdOrderByEntryIdAsc(txnId);

        List<Long> glIds = entries.stream()
                .map(BankLedgerEntry::getGlId)
                .distinct()
                .toList();

        Map<Long, BankLedgerAccount> glMap = glRepository
                .findAllById(glIds)
                .stream()
                .collect(Collectors.toMap(
                        BankLedgerAccount::getGlId,
                        Function.identity()
                ));

        List<TransactionLedgerEntryResponse> ledgerEntries =
                entries.stream()
                        .map(entry -> {
                            BankLedgerAccount gl = glMap.get(
                                    entry.getGlId()
                            );

                            if (gl == null) {
                                throw new IllegalStateException(
                                        "GL account missing: "
                                                + entry.getGlId()
                                );
                            }

                            return new TransactionLedgerEntryResponse(
                                    entry.getEntryId(),
                                    entry.getGlId(),
                                    gl.getGlCd(),
                                    gl.getGlNm(),
                                    entry.getAcctId(),
                                    entry.getEntrySide(),
                                    entry.getEntryAmt(),
                                    entry.getCrtDt()
                            );
                        })
                        .toList();

        BigDecimal totalDebit = entries.stream()
                .filter(e -> "DEBIT".equals(e.getEntrySide()))
                .map(BankLedgerEntry::getEntryAmt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = entries.stream()
                .filter(e -> "CREDIT".equals(e.getEntrySide()))
                .map(BankLedgerEntry::getEntryAmt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean balanced =
                !entries.isEmpty()
                        && totalDebit.compareTo(totalCredit) == 0;

        return new TransactionDetailResponse(
                transaction.getTxnId(),
                transaction.getTxnRef(),
                transaction.getAcctId(),
                transaction.getDestAcctId(),
                transaction.getTxnTyp(),
                transaction.getTxnAmt(),
                transaction.getCcyCd(),
                transaction.getTxnSts(),
                transaction.getCrtDt(),
                transaction.getCrtBy(),
                totalDebit,
                totalCredit,
                balanced,
                ledgerEntries
        );
    }


    private TransactionHistoryResponse toHistoryResponse(
            BankTransaction transaction,
            Long requestedAcctId,
            Map<Long, BankTransaction> originals
    ) {
        BankTransactionType type = BankTransactionType.fromDatabase(transaction.getTxnTyp());
        String direction = "N/A";

        if (type != null) {
            switch (type) {
                case DEPOSIT, FD_INTEREST_PAYOUT, FD_PREMATURE_WITHDRAWAL -> direction = "IN";
                case WITHDRAWAL, FD_PLACEMENT -> direction = "OUT";
                case TRANSFER -> direction = requestedAcctId.equals(transaction.getAcctId())
                        ? "OUT" : "IN";
                // Accrual and renewal are accounting events, not cash movements
                // affecting the bank account's available or ledger balance.
                case FD_INTEREST_ACCRUAL, FD_RENEWAL -> direction = "N/A";
                case REVERSAL -> {
                    BankTransaction original = originals.get(transaction.getReversalOfTxnId());
                    BankTransactionType originalType = original == null ? null
                            : BankTransactionType.fromDatabase(original.getTxnTyp());
                    if (originalType != null) {
                        switch (originalType) {
                            case DEPOSIT -> direction = "OUT";
                            case WITHDRAWAL -> direction = "IN";
                            case TRANSFER -> {
                                // Reversal stores the actual reversed flow:
                                // original destination -> original source.
                                if (requestedAcctId.equals(transaction.getAcctId())) {
                                    direction = "OUT";
                                } else if (requestedAcctId.equals(transaction.getDestAcctId())) {
                                    direction = "IN";
                                }
                            }
                            default -> direction = "N/A";
                        }
                    }
                }
            }
        }

        BigDecimal signedAmount = switch (direction) {
            case "OUT" -> transaction.getTxnAmt().negate();
            case "IN" -> transaction.getTxnAmt();
            default -> BigDecimal.ZERO; // No supported account-balance direction
        };

        return new TransactionHistoryResponse(
                transaction.getTxnId(),
                transaction.getTxnRef(),
                transaction.getAcctId(),
                transaction.getDestAcctId(),
                transaction.getTxnTyp(),
                direction,
                transaction.getTxnAmt(),
                signedAmount,
                transaction.getCcyCd(),
                transaction.getTxnSts(),
                transaction.getCrtDt(),
                transaction.getCrtBy()
        );
    }
}
