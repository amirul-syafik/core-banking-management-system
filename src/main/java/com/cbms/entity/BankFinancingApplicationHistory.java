package com.cbms.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_financing_application_history", schema = "public")
public class BankFinancingApplicationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hist_id")
    private Long histId;

    @Column(name = "appl_id", nullable = false)
    private Long applId;

    @Column(name = "old_sts")
    private String oldSts;

    @Column(name = "new_sts", nullable = false)
    private String newSts;

    @Column(name = "action_typ", nullable = false)
    private String actionTyp;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "action_by", nullable = false)
    private String actionBy;

    @Column(name = "action_dt", nullable = false)
    private OffsetDateTime actionDt;

    @PrePersist
    public void prePersist() {
        if (actionDt == null) {
            actionDt = OffsetDateTime.now();
        }
    }

    public Long getHistId() {
        return histId;
    }

    public void setHistId(Long v) {
        histId = v;
    }

    public Long getApplId() {
        return applId;
    }

    public void setApplId(Long v) {
        applId = v;
    }

    public String getOldSts() {
        return oldSts;
    }

    public void setOldSts(String v) {
        oldSts = v;
    }

    public String getNewSts() {
        return newSts;
    }

    public void setNewSts(String v) {
        newSts = v;
    }

    public String getActionTyp() {
        return actionTyp;
    }

    public void setActionTyp(String v) {
        actionTyp = v;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String v) {
        remarks = v;
    }

    public String getActionBy() {
        return actionBy;
    }

    public void setActionBy(String v) {
        actionBy = v;
    }

    public OffsetDateTime getActionDt() {
        return actionDt;
    }

    public void setActionDt(OffsetDateTime v) {
        actionDt = v;
    }
}
