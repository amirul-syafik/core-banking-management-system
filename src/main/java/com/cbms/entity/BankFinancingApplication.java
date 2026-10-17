package com.cbms.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_financing_application", schema = "public")
public class BankFinancingApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appl_id")
    private Long applId;

    @Column(name = "appl_ref", nullable = false, unique = true)
    private String applRef;

    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "prod_id", nullable = false)
    private Long prodId;

    @Column(name = "schm_id", nullable = false)
    private Long schmId;

    @Column(name = "fac_typ", nullable = false)
    private String facTyp;

    @Column(name = "banking_typ", nullable = false)
    private String bankingTyp;

    @Column(name = "ccy_cd", nullable = false)
    private String ccyCd;

    @Column(name = "req_amt", nullable = false)
    private BigDecimal reqAmt;

    @Column(name = "req_tenor", nullable = false)
    private Integer reqTenor;

    @Column(name = "financing_purpose")
    private String financingPurpose;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "appl_sts", nullable = false)
    private String applSts;

    @Column(name = "appr_amt")
    private BigDecimal apprAmt;

    @Column(name = "appr_tenor")
    private Integer apprTenor;

    @Column(name = "appr_rate")
    private BigDecimal apprRate;

    @Column(name = "fac_id")
    private Long facId;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "submitted_dt")
    private OffsetDateTime submittedDt;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_dt")
    private OffsetDateTime reviewedDt;

    @Column(name = "review_remarks")
    private String reviewRemarks;

    @Column(name = "crt_dt", nullable = false)
    private OffsetDateTime crtDt;

    @Column(name = "crt_by", nullable = false)
    private String crtBy;

    @Column(name = "upd_dt", nullable = false)
    private OffsetDateTime updDt;

    @Column(name = "upd_by")
    private String updBy;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();

        if (crtDt == null) crtDt = now;
        if (updDt == null) updDt = now;
        if (applSts == null) applSts = "DRAFT";
    }

    @PreUpdate
    public void preUpdate() {
        updDt = OffsetDateTime.now();
    }

    public Long getApplId() { return applId; }
    public void setApplId(Long v) { applId = v; }

    public String getApplRef() { return applRef; }
    public void setApplRef(String v) { applRef = v; }

    public Long getCustId() { return custId; }
    public void setCustId(Long v) { custId = v; }

    public Long getProdId() { return prodId; }
    public void setProdId(Long v) { prodId = v; }

    public Long getSchmId() { return schmId; }
    public void setSchmId(Long v) { schmId = v; }

    public String getFacTyp() { return facTyp; }
    public void setFacTyp(String v) { facTyp = v; }

    public String getBankingTyp() { return bankingTyp; }
    public void setBankingTyp(String v) { bankingTyp = v; }

    public String getCcyCd() { return ccyCd; }
    public void setCcyCd(String v) { ccyCd = v; }

    public BigDecimal getReqAmt() { return reqAmt; }
    public void setReqAmt(BigDecimal v) { reqAmt = v; }

    public Integer getReqTenor() { return reqTenor; }
    public void setReqTenor(Integer v) { reqTenor = v; }

    public String getFinancingPurpose() { return financingPurpose; }
    public void setFinancingPurpose(String v) { financingPurpose = v; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { remarks = v; }

    public String getApplSts() { return applSts; }
    public void setApplSts(String v) { applSts = v; }

    public BigDecimal getApprAmt() { return apprAmt; }
    public void setApprAmt(BigDecimal v) { apprAmt = v; }

    public Integer getApprTenor() { return apprTenor; }
    public void setApprTenor(Integer v) { apprTenor = v; }

    public BigDecimal getApprRate() { return apprRate; }
    public void setApprRate(BigDecimal v) { apprRate = v; }

    public Long getFacId() { return facId; }
    public void setFacId(Long v) { facId = v; }

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String v) { submittedBy = v; }

    public OffsetDateTime getSubmittedDt() { return submittedDt; }
    public void setSubmittedDt(OffsetDateTime v) { submittedDt = v; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String v) { reviewedBy = v; }

    public OffsetDateTime getReviewedDt() { return reviewedDt; }
    public void setReviewedDt(OffsetDateTime v) { reviewedDt = v; }

    public String getReviewRemarks() { return reviewRemarks; }
    public void setReviewRemarks(String v) { reviewRemarks = v; }

    public OffsetDateTime getCrtDt() { return crtDt; }
    public void setCrtDt(OffsetDateTime v) { crtDt = v; }

    public String getCrtBy() { return crtBy; }
    public void setCrtBy(String v) { crtBy = v; }

    public OffsetDateTime getUpdDt() { return updDt; }
    public void setUpdDt(OffsetDateTime v) { updDt = v; }

    public String getUpdBy() { return updBy; }
    public void setUpdBy(String v) { updBy = v; }
}
