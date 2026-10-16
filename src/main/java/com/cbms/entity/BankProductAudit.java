package com.cbms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "bank_product_audit", schema = "public")
public class BankProductAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "prod_id", nullable = false)
    private Long prodId;

    @Column(name = "action_typ", nullable = false)
    private String actionTyp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private String newValue;

    @Column(name = "action_by", nullable = false)
    private String actionBy;

    @Column(name = "action_dt", nullable = false)
    private OffsetDateTime actionDt;

    @PrePersist
    public void prePersist() {
        actionDt = OffsetDateTime.now();
    }

    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }

    public Long getProdId() { return prodId; }
    public void setProdId(Long prodId) { this.prodId = prodId; }

    public String getActionTyp() { return actionTyp; }
    public void setActionTyp(String actionTyp) { this.actionTyp = actionTyp; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public String getActionBy() { return actionBy; }
    public void setActionBy(String actionBy) { this.actionBy = actionBy; }

    public OffsetDateTime getActionDt() { return actionDt; }
    public void setActionDt(OffsetDateTime actionDt) { this.actionDt = actionDt; }
}