package com.cbms.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_account_application_history", schema = "public")
public class BankAccountApplicationHistory {

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

    @Column(name = "remarks", length = 1000)
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

    public void setHistId(Long value) {
        histId = value;
    }

    public Long getApplId() {
        return applId;
    }

    public void setApplId(Long value) {
        applId = value;
    }

    public String getOldSts() {
        return oldSts;
    }

    public void setOldSts(String value) {
        oldSts = value;
    }

    public String getNewSts() {
        return newSts;
    }

    public void setNewSts(String value) {
        newSts = value;
    }

    public String getActionTyp() {
        return actionTyp;
    }

    public void setActionTyp(String value) {
        actionTyp = value;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String value) {
        remarks = value;
    }

    public String getActionBy() {
        return actionBy;
    }

    public void setActionBy(String value) {
        actionBy = value;
    }

    public OffsetDateTime getActionDt() {
        return actionDt;
    }

    public void setActionDt(OffsetDateTime value) {
        actionDt = value;
    }
}