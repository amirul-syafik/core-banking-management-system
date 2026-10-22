package com.cbms.service;

import com.cbms.dto.TransactionReversalRequest;
import com.cbms.entity.BankTransaction;
import com.cbms.entity.TransactionReversalApproval;
import com.cbms.repository.BankTransactionRepository;
import com.cbms.repository.TransactionReversalApprovalRepository;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Set;

@Service
public class TransactionReversalApprovalService {
    private static final Set<String> ELIGIBLE = Set.of("DEPOSIT", "WITHDRAWAL", "TRANSFER");
    private final TransactionReversalApprovalRepository requests;
    private final BankTransactionRepository transactions;
    private final BankTransactionReversalService posting;

    public TransactionReversalApprovalService(TransactionReversalApprovalRepository requests,
                                              BankTransactionRepository transactions, BankTransactionReversalService posting) {
        this.requests = requests;
        this.transactions = transactions;
        this.posting = posting;
    }

    @Transactional(readOnly = true)
    public Page<TransactionReversalApproval> list(int page, int size) {
        var result = requests.findAllByOrderByRequestedAtDesc(PageRequest.of(Math.max(0, page), Math.min(50, Math.max(1, size))));
        result.forEach(r -> r.originalTxnRef = transactions.findById(r.originalTxnId).map(BankTransaction::getTxnRef).orElse("Unavailable"));
        return result;
    }

    /**
     * UI hint only: reverse() and submit() remain the authoritative eligibility checks.
     */
    @Transactional(readOnly = true)
    public java.util.Map<Long, String> eligibility(java.util.List<Long> txnIds) {
        java.util.Map<Long, String> result = new java.util.LinkedHashMap<>();
        for (Long id : txnIds.stream().filter(java.util.Objects::nonNull).distinct().limit(50).toList()) {
            var transaction = transactions.findById(id);
            if (transaction.isEmpty()) {
                result.put(id, "NOT_REVERSIBLE");
            } else if (transactions.existsByReversalOfTxnId(id)) {
                result.put(id, "REVERSED");
            } else if (requests.existsByOriginalTxnIdAndStatus(id, "PENDING")) {
                result.put(id, "PENDING");
            } else if (!"POSTED".equals(transaction.get().getTxnSts()) || !ELIGIBLE.contains(transaction.get().getTxnTyp())) {
                result.put(id, "NOT_REVERSIBLE");
            } else {
                result.put(id, "REVERSIBLE");
            }
        }
        return result;
    }

    @Transactional
    public TransactionReversalApproval submit(Long txnId, TransactionReversalRequest body, Authentication user) {
        validateReason(body.reason());
        BankTransaction txn = transactions.findById(txnId).orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        if (!"POSTED".equals(txn.getTxnSts()) || !ELIGIBLE.contains(txn.getTxnTyp()) ||
                transactions.existsByReversalOfTxnId(txnId))
            throw new IllegalArgumentException("Transaction is not eligible for reversal");
        if (requests.existsByOriginalTxnIdAndStatus(txnId, "PENDING"))
            throw new IllegalArgumentException("A reversal request is already pending");
        TransactionReversalApproval r = new TransactionReversalApproval();
        r.originalTxnId = txnId;
        r.reason = body.reason().trim();
        r.status = "PENDING";
        r.requestedBy = user.getName();
        r.requestedAt = OffsetDateTime.now();
        return requests.saveAndFlush(r);
    }

    @Transactional
    public TransactionReversalApproval decide(Long id, boolean approve, String reason, Authentication user) {
        TransactionReversalApproval r = requests.lockById(id).orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!"PENDING".equals(r.status)) throw new IllegalArgumentException("Request has already been decided");
        if (user.getName().equalsIgnoreCase(r.requestedBy))
            throw new IllegalArgumentException("Requester cannot approve or reject their own request");
        if (!approve && (reason == null || reason.isBlank()))
            throw new IllegalArgumentException("Rejection reason is required");
        if (reason != null && reason.length() > 250)
            throw new IllegalArgumentException("Decision reason must be at most 250 characters");
        if (approve) {
            // This call runs in the same database transaction; any posting failure rolls back the decision.
            var result = posting.reverse(r.originalTxnId, new TransactionReversalRequest(r.reason),
                    "REVERSAL-REQUEST-" + r.id, user);
            r.reversalTxnId = result.reversalTxnId();
            r.status = "APPROVED";
        } else {
            r.status = "REJECTED";
        }
        r.decisionReason = reason == null ? null : reason.trim();
        r.decidedBy = user.getName();
        r.decidedAt = OffsetDateTime.now();
        return requests.save(r);
    }

    private void validateReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 250)
            throw new IllegalArgumentException("Reversal reason is required (max 250 characters)");
    }
}
