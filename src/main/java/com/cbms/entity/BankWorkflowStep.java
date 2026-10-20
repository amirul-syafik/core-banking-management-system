package com.cbms.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_workflow_step", schema = "public")
public class BankWorkflowStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "step_id")
    private Long stepId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false)
    private BankWorkflowDefinition workflow;
    @Column(name = "step_no", nullable = false)
    private Integer stepNo;
    @Column(name = "step_nm", nullable = false, length = 150)
    private String stepNm;
    @Column(name = "approver_role", nullable = false, length = 50)
    private String approverRole;
    @Column(name = "required_approvals", nullable = false)
    private Integer requiredApprovals;
    @Column(name = "step_sts", nullable = false, length = 20)
    private String stepSts;
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
        crtDt = now;
        updDt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updDt = OffsetDateTime.now();
    }

    public Long getStepId() {
        return stepId;
    }

    public BankWorkflowDefinition getWorkflow() {
        return workflow;
    }

    public void setWorkflow(BankWorkflowDefinition v) {
        workflow = v;
    }

    public Integer getStepNo() {
        return stepNo;
    }

    public void setStepNo(Integer v) {
        stepNo = v;
    }

    public String getStepNm() {
        return stepNm;
    }

    public void setStepNm(String v) {
        stepNm = v;
    }

    public String getApproverRole() {
        return approverRole;
    }

    public void setApproverRole(String v) {
        approverRole = v;
    }

    public Integer getRequiredApprovals() {
        return requiredApprovals;
    }

    public void setRequiredApprovals(Integer v) {
        requiredApprovals = v;
    }

    public String getStepSts() {
        return stepSts;
    }

    public void setStepSts(String v) {
        stepSts = v;
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
