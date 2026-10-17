package com.cbms.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_account_application", schema = "public")
public class BankAccountApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appl_id")
    private Long applId;

    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "prod_id", nullable = false)
    private Long prodId;

    @Column(name = "schm_id", nullable = false)
    private Long schmId;

    @Column(name = "appl_ref", nullable = false, length = 50)
    private String applRef;

    @Column(name = "appl_sts", nullable = false, length = 20)
    private String applSts;

    @Column(name = "req_amt", precision = 19, scale = 2)
    private BigDecimal reqAmt;

    @Column(name = "req_tenor")
    private Integer reqTenor;

    @Column(name = "remarks", length = 1000)
    private String remarks;

    @Column(name = "acct_id")
    private Long acctId;

    @Column(name = "crt_dt", nullable = false, updatable = false)
    private OffsetDateTime crtDt;

    @Column(name = "crt_by", nullable = false, updatable = false)
    private String crtBy;

    @Column(name = "upd_dt", nullable = false)
    private OffsetDateTime updDt;

    @Column(name = "upd_by")
    private String updBy;

    @Column(name = "submitted_by", length = 100)
    private String submittedBy;

    @Column(name = "submitted_dt")
    private OffsetDateTime submittedDt;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_dt")
    private OffsetDateTime reviewedDt;

    @Column(name = "review_remarks", length = 1000)
    private String reviewRemarks;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        crtDt = now;
        updDt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updDt = OffsetDateTime.now();
    }

    public Long getApplId() { return applId; }
    public void setApplId(Long value) { applId = value; }

    public Long getCustId() { return custId; }
    public void setCustId(Long value) { custId = value; }

    public Long getProdId() { return prodId; }
    public void setProdId(Long value) { prodId = value; }

    public Long getSchmId() { return schmId; }
    public void setSchmId(Long value) { schmId = value; }

    public String getApplRef() { return applRef; }
    public void setApplRef(String value) { applRef = value; }

    public String getApplSts() { return applSts; }
    public void setApplSts(String value) { applSts = value; }

    public BigDecimal getReqAmt() { return reqAmt; }
    public void setReqAmt(BigDecimal value) { reqAmt = value; }

    public Integer getReqTenor() { return reqTenor; }
    public void setReqTenor(Integer value) { reqTenor = value; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String value) { remarks = value; }

    public Long getAcctId() { return acctId; }
    public void setAcctId(Long value) { acctId = value; }

    public OffsetDateTime getCrtDt() { return crtDt; }
    public void setCrtDt(OffsetDateTime value) { crtDt = value; }

    public String getCrtBy() { return crtBy; }
    public void setCrtBy(String value) { crtBy = value; }

    public OffsetDateTime getUpdDt() { return updDt; }
    public void setUpdDt(OffsetDateTime value) { updDt = value; }

    public String getUpdBy() { return updBy; }
    public void setUpdBy(String value) { updBy = value; }

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String value) { submittedBy = value; }

    public OffsetDateTime getSubmittedDt() { return submittedDt; }
    public void setSubmittedDt(OffsetDateTime value) { submittedDt = value; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String value) { reviewedBy = value; }

    public OffsetDateTime getReviewedDt() { return reviewedDt; }
    public void setReviewedDt(OffsetDateTime value) { reviewedDt = value; }

    public String getReviewRemarks() { return reviewRemarks; }
    public void setReviewRemarks(String value) { reviewRemarks = value; }
}