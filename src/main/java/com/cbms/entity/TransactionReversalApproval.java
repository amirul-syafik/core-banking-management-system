package com.cbms.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_transaction_reversal_request")
public class TransactionReversalApproval {
    @Transient
    public String originalTxnRef;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    public Long id;
    @Column(name = "original_txn_id", nullable = false)
    public Long originalTxnId;
    @Column(name = "reason", nullable = false, length = 250)
    public String reason;
    @Column(name = "status", nullable = false, length = 20)
    public String status;
    @Column(name = "requested_by", nullable = false, length = 100)
    public String requestedBy;
    @Column(name = "requested_at", nullable = false)
    public OffsetDateTime requestedAt;
    @Column(name = "decided_by", length = 100)
    public String decidedBy;
    @Column(name = "decided_at")
    public OffsetDateTime decidedAt;
    @Column(name = "decision_reason", length = 250)
    public String decisionReason;
    @Column(name = "reversal_txn_id")
    public Long reversalTxnId;
}
