package com.cbms.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_account_audit_history")
public class BankAccountAuditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "acct_id", nullable = false)
    private Long acctId;

    @Column(name = "action_typ", nullable = false, length = 30)
    private String actionTyp;

    @Column(name = "old_val", length = 100)
    private String oldVal;

    @Column(name = "new_val", length = 100)
    private String newVal;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "changed_dt", nullable = false)
    private OffsetDateTime changedDt;

    @PrePersist
    public void prePersist() {
        if (changedDt == null) {
            changedDt = OffsetDateTime.now();
        }
    }

    public Long getAuditId() {
        return auditId;
    }

    public void setAuditId(Long auditId) {
        this.auditId = auditId;
    }

    public Long getAcctId() {
        return acctId;
    }

    public void setAcctId(Long acctId) {
        this.acctId = acctId;
    }

    public String getActionTyp() {
        return actionTyp;
    }

    public void setActionTyp(String actionTyp) {
        this.actionTyp = actionTyp;
    }

    public String getOldVal() {
        return oldVal;
    }

    public void setOldVal(String oldVal) {
        this.oldVal = oldVal;
    }

    public String getNewVal() {
        return newVal;
    }

    public void setNewVal(String newVal) {
        this.newVal = newVal;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public OffsetDateTime getChangedDt() {
        return changedDt;
    }

    public void setChangedDt(OffsetDateTime changedDt) {
        this.changedDt = changedDt;
    }
}
