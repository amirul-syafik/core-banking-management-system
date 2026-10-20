package com.cbms.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_workflow_history", schema = "public")
public class BankWorkflowHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hist_id")
    private Long histId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instance_id", nullable = false, updatable = false)
    private BankWorkflowInstance instance;

    @Column(name = "task_id", updatable = false)
    private Long taskId;

    @Column(name = "action_cd", nullable = false, length = 50, updatable = false)
    private String actionCd;

    @Column(name = "from_sts", length = 20, updatable = false)
    private String fromSts;

    @Column(name = "to_sts", nullable = false, length = 20, updatable = false)
    private String toSts;

    @Column(name = "action_remarks", length = 1000, updatable = false)
    private String actionRemarks;

    @Column(name = "action_by", nullable = false, length = 100, updatable = false)
    private String actionBy;

    @Column(name = "action_dt", nullable = false, updatable = false)
    private OffsetDateTime actionDt;

    @PrePersist
    public void onCreate() {
        if (actionDt == null) actionDt = OffsetDateTime.now();
    }

    public Long getHistId() {
        return histId;
    }

    public BankWorkflowInstance getInstance() {
        return instance;
    }

    public void setInstance(BankWorkflowInstance value) {
        instance = value;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long value) {
        taskId = value;
    }

    public String getActionCd() {
        return actionCd;
    }

    public void setActionCd(String value) {
        actionCd = value;
    }

    public String getFromSts() {
        return fromSts;
    }

    public void setFromSts(String value) {
        fromSts = value;
    }

    public String getToSts() {
        return toSts;
    }

    public void setToSts(String value) {
        toSts = value;
    }

    public String getActionRemarks() {
        return actionRemarks;
    }

    public void setActionRemarks(String value) {
        actionRemarks = value;
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
}
