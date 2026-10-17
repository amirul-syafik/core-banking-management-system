package com.cbms.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_credit_line_facility", schema = "public")
public class BankCreditLineFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cl_fac_id")
    private Long clFacId;

    @Column(name = "cl_fac_ref", nullable = false, unique = true)
    private String clFacRef;

    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "prod_id", nullable = false)
    private Long prodId;

    @Column(name = "schm_id", nullable = false)
    private Long schmId;

    @Column(name = "cl_fac_typ", nullable = false)
    private String clFacTyp;

    @Column(name = "ccy_cd", nullable = false)
    private String ccyCd;

    @Column(name = "appr_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal apprLimit;

    @Column(name = "utilized_amt", nullable = false, precision = 19, scale = 2)
    private BigDecimal utilizedAmt;

    @Column(name = "available_limit", insertable = false, updatable = false)
    private BigDecimal availableLimit;

    @Column(name = "eff_dt", nullable = false)
    private LocalDate effDt;

    @Column(name = "expiry_dt", nullable = false)
    private LocalDate expiryDt;

    @Column(name = "rate_typ", nullable = false)
    private String rateTyp;

    @Column(name = "eff_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal effRate;

    @Column(name = "cl_fac_sts", nullable = false)
    private String clFacSts;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "crt_dt", nullable = false)
    private OffsetDateTime crtDt;

    @Column(name = "crt_by", nullable = false)
    private String crtBy;

    @Column(name = "upd_dt")
    private OffsetDateTime updDt;

    @Column(name = "upd_by")
    private String updBy;

    public Long getClFacId() { return clFacId; }
    public void setClFacId(Long value) { clFacId = value; }

    public String getClFacRef() { return clFacRef; }
    public void setClFacRef(String value) { clFacRef = value; }

    public Long getCustId() { return custId; }
    public void setCustId(Long value) { custId = value; }

    public Long getProdId() { return prodId; }
    public void setProdId(Long value) { prodId = value; }

    public Long getSchmId() { return schmId; }
    public void setSchmId(Long value) { schmId = value; }

    public String getClFacTyp() { return clFacTyp; }
    public void setClFacTyp(String value) { clFacTyp = value; }

    public String getCcyCd() { return ccyCd; }
    public void setCcyCd(String value) { ccyCd = value; }

    public BigDecimal getApprLimit() { return apprLimit; }
    public void setApprLimit(BigDecimal value) { apprLimit = value; }

    public BigDecimal getUtilizedAmt() { return utilizedAmt; }
    public void setUtilizedAmt(BigDecimal value) { utilizedAmt = value; }

    public BigDecimal getAvailableLimit() { return availableLimit; }

    public LocalDate getEffDt() { return effDt; }
    public void setEffDt(LocalDate value) { effDt = value; }

    public LocalDate getExpiryDt() { return expiryDt; }
    public void setExpiryDt(LocalDate value) { expiryDt = value; }

    public String getRateTyp() { return rateTyp; }
    public void setRateTyp(String value) { rateTyp = value; }

    public BigDecimal getEffRate() { return effRate; }
    public void setEffRate(BigDecimal value) { effRate = value; }

    public String getClFacSts() { return clFacSts; }
    public void setClFacSts(String value) { clFacSts = value; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String value) { remarks = value; }

    public OffsetDateTime getCrtDt() { return crtDt; }
    public void setCrtDt(OffsetDateTime value) { crtDt = value; }

    public String getCrtBy() { return crtBy; }
    public void setCrtBy(String value) { crtBy = value; }

    public OffsetDateTime getUpdDt() { return updDt; }
    public void setUpdDt(OffsetDateTime value) { updDt = value; }

    public String getUpdBy() { return updBy; }
    public void setUpdBy(String value) { updBy = value; }
}
