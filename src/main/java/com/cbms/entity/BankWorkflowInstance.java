package com.cbms.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_workflow_instance", schema = "public")
public class BankWorkflowInstance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "instance_id")
    private Long instanceId;

    @Column(name = "instance_ref", nullable = false, unique = true, length = 100, updatable = false)
    private String instanceRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false, updatable = false)
    private BankWorkflowDefinition workflow;

    @Column(name = "workflow_version", nullable = false, updatable = false)
    private Integer workflowVersion;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private Long entityId;

    @Column(name = "entity_ref", length = 100, updatable = false)
    private String entityRef;

    @Column(name = "workflow_sts", nullable = false, length = 20)
    private String workflowSts;

    @Column(name = "current_step_no")
    private Integer currentStepNo;

    @Column(name = "submitted_by", nullable = false, length = 100, updatable = false)
    private String submittedBy;

    @Column(name = "submitted_dt", nullable = false, updatable = false)
    private OffsetDateTime submittedDt;

    @Column(name = "completed_by", length = 100)
    private String completedBy;

    @Column(name = "completed_dt")
    private OffsetDateTime completedDt;

    @Column(name = "remarks", length = 1000)
    private String remarks;

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
        if (submittedDt == null) submittedDt = now;
        if (crtDt == null) crtDt = now;
        updDt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updDt = OffsetDateTime.now();
    }

    public Long getInstanceId() {
        return instanceId;
    }

    public String getInstanceRef() {
        return instanceRef;
    }

    public void setInstanceRef(String value) {
        instanceRef = value;
    }

    public BankWorkflowDefinition getWorkflow() {
        return workflow;
    }

    public void setWorkflow(BankWorkflowDefinition value) {
        workflow = value;
    }

    public Integer getWorkflowVersion() {
        return workflowVersion;
    }

    public void setWorkflowVersion(Integer value) {
        workflowVersion = value;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long value) {
        entityId = value;
    }

    public String getEntityRef() {
        return entityRef;
    }

    public void setEntityRef(String value) {
        entityRef = value;
    }

    public String getWorkflowSts() {
        return workflowSts;
    }

    public void setWorkflowSts(String value) {
        workflowSts = value;
    }

    public Integer getCurrentStepNo() {
        return currentStepNo;
    }

    public void setCurrentStepNo(Integer value) {
        currentStepNo = value;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String value) {
        submittedBy = value;
    }

    public OffsetDateTime getSubmittedDt() {
        return submittedDt;
    }

    public String getCompletedBy() {
        return completedBy;
    }

    public void setCompletedBy(String value) {
        completedBy = value;
    }

    public OffsetDateTime getCompletedDt() {
        return completedDt;
    }

    public void setCompletedDt(OffsetDateTime completedDt) {
        this.completedDt = completedDt;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String value) {
        remarks = value;
    }

    public OffsetDateTime getCrtDt() {
        return crtDt;
    }

    public String getCrtBy() {
        return crtBy;
    }

    public void setCrtBy(String value) {
        crtBy = value;
    }

    public OffsetDateTime getUpdDt() {
        return updDt;
    }

    public String getUpdBy() {
        return updBy;
    }

    public void setUpdBy(String value) {
        updBy = value;
    }
}
