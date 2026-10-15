package com.cbms.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_kyc")
public class CustomerKyc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "kyc_id")
    private Long kycId;

    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "asmt_sts", length = 20)
    private String asmtSts;

    @Column(name = "risk_rtng", length = 20)
    private String riskRtng;

    @Column(name = "rev_dt")
    private LocalDate revDt;

    @Column(name = "nxt_rev_dt")
    private LocalDate nxtRevDt;

    @Column(name = "rmk", length = 1000)
    private String rmk;

    @Column(name = "crt_dt")
    private OffsetDateTime crtDt;

    @Column(name = "crt_by")
    private String crtBy;

    @Column(name = "upd_dt")
    private OffsetDateTime updDt;

    @Column(name = "upd_by")
    private String updBy;

    public CustomerKyc() {
    }

    public Long getKycId() {
        return kycId;
    }

    public void setKycId(Long kycId) {
        this.kycId = kycId;
    }

    public Long getCustId() {
        return custId;
    }

    public void setCustId(Long custId) {
        this.custId = custId;
    }

    public String getAsmtSts() {
        return asmtSts;
    }

    public void setAsmtSts(String asmtSts) {
        this.asmtSts = asmtSts;
    }

    public String getRiskRtng() {
        return riskRtng;
    }

    public void setRiskRtng(String riskRtng) {
        this.riskRtng = riskRtng;
    }

    public LocalDate getRevDt() {
        return revDt;
    }

    public void setRevDt(LocalDate revDt) {
        this.revDt = revDt;
    }

    public LocalDate getNxtRevDt() {
        return nxtRevDt;
    }

    public void setNxtRevDt(LocalDate nxtRevDt) {
        this.nxtRevDt = nxtRevDt;
    }

    public String getRmk() {
        return rmk;
    }

    public void setRmk(String rmk) {
        this.rmk = rmk;
    }

    public OffsetDateTime getCrtDt() {
        return crtDt;
    }

    public void setCrtDt(OffsetDateTime crtDt) {
        this.crtDt = crtDt;
    }

    public String getCrtBy() {
        return crtBy;
    }

    public void setCrtBy(String crtBy) {
        this.crtBy = crtBy;
    }

    public OffsetDateTime getUpdDt() {
        return updDt;
    }

    public void setUpdDt(OffsetDateTime updDt) {
        this.updDt = updDt;
    }

    public String getUpdBy() {
        return updBy;
    }

    public void setUpdBy(String updBy) {
        this.updBy = updBy;
    }
}