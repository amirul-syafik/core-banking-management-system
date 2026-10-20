package com.cbms.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_workflow_task", schema = "public")
public class BankWorkflowTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Long taskId;
    @Column(name = "task_ref", nullable = false, unique = true, length = 100, updatable = false)
    private String taskRef;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instance_id", nullable = false, updatable = false)
    private BankWorkflowInstance instance;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_id", nullable = false, updatable = false)
    private BankWorkflowStep step;
    @Column(name = "assigned_role", nullable = false, length = 50)
    private String assignedRole;
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;
    @Column(name = "task_sts", nullable = false, length = 20)
    private String taskSts;
    @Column(name = "assigned_dt", nullable = false)
    private OffsetDateTime assignedDt;
    @Column(name = "claimed_dt")
    private OffsetDateTime claimedDt;
    @Column(name = "action_by", length = 100)
    private String actionBy;
    @Column(name = "action_dt")
    private OffsetDateTime actionDt;
    @Column(name = "action_remarks", length = 1000)
    private String actionRemarks;
    @Column(name = "crt_dt", nullable = false, updatable = false)
    private OffsetDateTime crtDt;
    @Column(name = "crt_by", nullable = false, updatable = false, length = 100)
    private String crtBy;
    @Column(name = "upd_dt", nullable = false)
    private OffsetDateTime updDt;
    @Column(name = "upd_by", length = 100)
    private String updBy;

    @PrePersist
    public void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (assignedDt == null) assignedDt = now;
        if (crtDt == null) crtDt = now;
        updDt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updDt = OffsetDateTime.now();
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getTaskRef() {
        return taskRef;
    }

    public void setTaskRef(String v) {
        taskRef = v;
    }

    public BankWorkflowInstance getInstance() {
        return instance;
    }

    public void setInstance(BankWorkflowInstance v) {
        instance = v;
    }

    public BankWorkflowStep getStep() {
        return step;
    }

    public void setStep(BankWorkflowStep v) {
        step = v;
    }

    public String getAssignedRole() {
        return assignedRole;
    }

    public void setAssignedRole(String v) {
        assignedRole = v;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String v) {
        assignedTo = v;
    }

    public String getTaskSts() {
        return taskSts;
    }

    public void setTaskSts(String v) {
        taskSts = v;
    }

    public OffsetDateTime getAssignedDt() {
        return assignedDt;
    }

    public void setAssignedDt(OffsetDateTime v) {
        assignedDt = v;
    }

    public OffsetDateTime getClaimedDt() {
        return claimedDt;
    }

    public void setClaimedDt(OffsetDateTime v) {
        claimedDt = v;
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

    public String getActionRemarks() {
        return actionRemarks;
    }

    public void setActionRemarks(String v) {
        actionRemarks = v;
    }

    public OffsetDateTime getCrtDt() {
        return crtDt;
    }

    public String getCrtBy() {
        return crtBy;
    }

    public void setCrtBy(String v) {
        crtBy = v;
    }

    public OffsetDateTime getUpdDt() {
        return updDt;
    }

    public String getUpdBy() {
        return updBy;
    }

    public void setUpdBy(String v) {
        updBy = v;
    }
}
