package com.cbms.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_workflow_definition", schema = "public")
public class BankWorkflowDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "workflow_id")
    private Long workflowId;
    @Column(name = "workflow_cd", nullable = false, length = 50, updatable = false)
    private String workflowCd;
    @Column(name = "workflow_nm", nullable = false, length = 150)
    private String workflowNm;
    @Column(name = "workflow_desc", length = 500)
    private String workflowDesc;
    @Column(name = "module_cd", nullable = false, length = 50, updatable = false)
    private String moduleCd;
    @Column(name = "entity_typ", nullable = false, length = 100, updatable = false)
    private String entityTyp;
    @Column(name = "workflow_sts", nullable = false, length = 20)
    private String workflowSts;
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;
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

    public Long getWorkflowId() {
        return workflowId;
    }

    public String getWorkflowCd() {
        return workflowCd;
    }

    public void setWorkflowCd(String v) {
        workflowCd = v;
    }

    public String getWorkflowNm() {
        return workflowNm;
    }

    public void setWorkflowNm(String v) {
        workflowNm = v;
    }

    public String getWorkflowDesc() {
        return workflowDesc;
    }

    public void setWorkflowDesc(String v) {
        workflowDesc = v;
    }

    public String getModuleCd() {
        return moduleCd;
    }

    public void setModuleCd(String v) {
        moduleCd = v;
    }

    public String getEntityTyp() {
        return entityTyp;
    }

    public void setEntityTyp(String v) {
        entityTyp = v;
    }

    public String getWorkflowSts() {
        return workflowSts;
    }

    public void setWorkflowSts(String v) {
        workflowSts = v;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer v) {
        versionNo = v;
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
