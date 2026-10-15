package com.cbms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_audit_history")
public class CustomerAuditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "action_typ", nullable = false, length = 30)
    private String actionTyp;

    @Column(name = "old_val", columnDefinition = "text")
    private String oldVal;

    @Column(name = "new_val", columnDefinition = "text")
    private String newVal;

    @Column(name = "changed_by", nullable = false, length = 50)
    private String changedBy;

    @Column(name = "changed_dt", nullable = false)
    private OffsetDateTime changedDt;

    public CustomerAuditHistory() {
    }

    public Long getAuditId() {
        return auditId;
    }

    public Long getCustId() {
        return custId;
    }

    public void setCustId(Long custId) {
        this.custId = custId;
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
