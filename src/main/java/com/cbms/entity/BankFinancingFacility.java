package com.cbms.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_financing_facility", schema = "public")
public class BankFinancingFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fac_id")
    private Long facId;

    @Column(name = "fac_ref", nullable = false, unique = true)
    private String facRef;

    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "prod_id", nullable = false)
    private Long prodId;

    @Column(name = "schm_id", nullable = false)
    private Long schmId;

    @Column(name = "fac_typ", nullable = false)
    private String facTyp;

    @Column(name = "ccy_cd", nullable = false)
    private String ccyCd;

    @Column(name = "appr_amt", precision = 19, scale = 2)
    private BigDecimal apprAmt;

    @Column(name = "tenor_mth")
    private Integer tenorMth;

    @Column(name = "rate_typ")
    private String rateTyp;

    @Column(name = "eff_rate", precision = 9, scale = 6)
    private BigDecimal effRate;

    @Column(name = "fac_sts", nullable = false)
    private String facSts;

    @Column(name = "appr_dt")
    private LocalDate apprDt;

    @Column(name = "book_dt")
    private LocalDate bookDt;

    @Column(name = "maturity_dt")
    private LocalDate maturityDt;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "crt_dt")
    private OffsetDateTime crtDt;

    @Column(name = "crt_by")
    private String crtBy;

    @Column(name = "upd_dt")
    private OffsetDateTime updDt;

    @Column(name = "upd_by")
    private String updBy;

    @Column(name = "appl_id")
    private Long applId;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();

        if (crtDt == null) crtDt = now;
        if (updDt == null) updDt = now;
        if (facSts == null) facSts = "DRAFT";
        if (ccyCd == null) ccyCd = "MYR";
    }

    @PreUpdate
    public void preUpdate() {
        updDt = OffsetDateTime.now();
    }

    public Long getFacId() {
        return facId;
    }

    public void setFacId(Long value) {
        facId = value;
    }

    public String getFacRef() {
        return facRef;
    }

    public void setFacRef(String value) {
        facRef = value;
    }

    public Long getCustId() {
        return custId;
    }

    public void setCustId(Long value) {
        custId = value;
    }

    public Long getProdId() {
        return prodId;
    }

    public void setProdId(Long value) {
        prodId = value;
    }

    public Long getSchmId() {
        return schmId;
    }

    public void setSchmId(Long value) {
        schmId = value;
    }

    public String getFacTyp() {
        return facTyp;
    }

    public void setFacTyp(String value) {
        facTyp = value;
    }

    public String getCcyCd() {
        return ccyCd;
    }

    public void setCcyCd(String value) {
        ccyCd = value;
    }

    public BigDecimal getApprAmt() {
        return apprAmt;
    }

    public void setApprAmt(BigDecimal value) {
        apprAmt = value;
    }

    public Integer getTenorMth() {
        return tenorMth;
    }

    public void setTenorMth(Integer value) {
        tenorMth = value;
    }

    public String getRateTyp() {
        return rateTyp;
    }

    public void setRateTyp(String value) {
        rateTyp = value;
    }

    public BigDecimal getEffRate() {
        return effRate;
    }

    public void setEffRate(BigDecimal value) {
        effRate = value;
    }

    public String getFacSts() {
        return facSts;
    }

    public void setFacSts(String value) {
        facSts = value;
    }

    public LocalDate getApprDt() {
        return apprDt;
    }

    public void setApprDt(LocalDate value) {
        apprDt = value;
    }

    public LocalDate getBookDt() {
        return bookDt;
    }

    public void setBookDt(LocalDate value) {
        bookDt = value;
    }

    public LocalDate getMaturityDt() {
        return maturityDt;
    }

    public void setMaturityDt(LocalDate value) {
        maturityDt = value;
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

    public void setCrtDt(OffsetDateTime value) {
        crtDt = value;
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

    public void setUpdDt(OffsetDateTime value) {
        updDt = value;
    }

    public String getUpdBy() {
        return updBy;
    }

    public void setUpdBy(String value) {
        updBy = value;
    }

    public Long getApplId() {
        return applId;
    }

    public void setApplId(Long applId) {
        this.applId = applId;
    }
}
