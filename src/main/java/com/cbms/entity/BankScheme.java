package com.cbms.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_scheme", schema = "public")
public class BankScheme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schm_id")
    private Long schmId;

    @Column(name = "prod_id", nullable = false)
    private Long prodId;

    @Column(name = "schm_cd", nullable = false, length = 30)
    private String schmCd;

    @Column(name = "schm_nm", nullable = false, length = 150)
    private String schmNm;

    @Column(name = "schm_desc", length = 500)
    private String schmDesc;

    @Column(name = "min_amt", precision = 19, scale = 2)
    private BigDecimal minAmt;

    @Column(name = "max_amt", precision = 19, scale = 2)
    private BigDecimal maxAmt;

    @Column(name = "min_tenor")
    private Integer minTenor;

    @Column(name = "max_tenor")
    private Integer maxTenor;

    @Column(name = "eligibility_desc", length = 1000)
    private String eligibilityDesc;

    @Column(name = "schm_sts", nullable = false, length = 20)
    private String schmSts;

    @Column(name = "crt_dt", nullable = false, updatable = false)
    private OffsetDateTime crtDt;

    @Column(name = "crt_by", nullable = false, updatable = false)
    private String crtBy;

    @Column(name = "upd_dt", nullable = false)
    private OffsetDateTime updDt;

    @Column(name = "upd_by")
    private String updBy;

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

    public Long getSchmId() {
        return schmId;
    }

    public void setSchmId(Long schmId) {
        this.schmId = schmId;
    }

    public Long getProdId() {
        return prodId;
    }

    public void setProdId(Long prodId) {
        this.prodId = prodId;
    }

    public String getSchmCd() {
        return schmCd;
    }

    public void setSchmCd(String schmCd) {
        this.schmCd = schmCd;
    }

    public String getSchmNm() {
        return schmNm;
    }

    public void setSchmNm(String schmNm) {
        this.schmNm = schmNm;
    }

    public String getSchmDesc() {
        return schmDesc;
    }

    public void setSchmDesc(String schmDesc) {
        this.schmDesc = schmDesc;
    }

    public BigDecimal getMinAmt() {
        return minAmt;
    }

    public void setMinAmt(BigDecimal minAmt) {
        this.minAmt = minAmt;
    }

    public BigDecimal getMaxAmt() {
        return maxAmt;
    }

    public void setMaxAmt(BigDecimal maxAmt) {
        this.maxAmt = maxAmt;
    }

    public Integer getMinTenor() {
        return minTenor;
    }

    public void setMinTenor(Integer minTenor) {
        this.minTenor = minTenor;
    }

    public Integer getMaxTenor() {
        return maxTenor;
    }

    public void setMaxTenor(Integer maxTenor) {
        this.maxTenor = maxTenor;
    }

    public String getEligibilityDesc() {
        return eligibilityDesc;
    }

    public void setEligibilityDesc(String eligibilityDesc) {
        this.eligibilityDesc = eligibilityDesc;
    }

    public String getSchmSts() {
        return schmSts;
    }

    public void setSchmSts(String schmSts) {
        this.schmSts = schmSts;
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
