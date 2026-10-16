package com.cbms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_scheme_audit", schema = "public")
public class BankSchemeAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "schm_id", nullable = false)
    private Long schmId;

    @Column(name = "action_typ", nullable = false, length = 30)
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

    public Long getSchmId() { return schmId; }
    public void setSchmId(Long schmId) { this.schmId = schmId; }

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