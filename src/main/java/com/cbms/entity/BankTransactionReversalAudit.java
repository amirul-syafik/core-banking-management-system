package com.cbms.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_transaction_reversal_audit")
public class BankTransactionReversalAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "original_txn_id", nullable = false)
    private Long originalTxnId;

    @Column(name = "reversal_txn_id", nullable = false)
    private Long reversalTxnId;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "reversed_by", nullable = false)
    private String reversedBy;

    @Column(name = "reversed_dt", nullable = false)
    private OffsetDateTime reversedDt;

    public Long getAuditId() {
        return auditId;
    }

    public Long getOriginalTxnId() {
        return originalTxnId;
    }

    public void setOriginalTxnId(Long originalTxnId) {
        this.originalTxnId = originalTxnId;
    }

    public Long getReversalTxnId() {
        return reversalTxnId;
    }

    public void setReversalTxnId(Long reversalTxnId) {
        this.reversalTxnId = reversalTxnId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getReversedBy() {
        return reversedBy;
    }

    public void setReversedBy(String reversedBy) {
        this.reversedBy = reversedBy;
    }

    public OffsetDateTime getReversedDt() {
        return reversedDt;
    }

    public void setReversedDt(OffsetDateTime reversedDt) {
        this.reversedDt = reversedDt;
    }
}
