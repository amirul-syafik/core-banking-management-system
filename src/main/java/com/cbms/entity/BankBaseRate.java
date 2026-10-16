package com.cbms.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_base_rate", schema = "public")
public class BankBaseRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "base_rate_id")
    private Long baseRateId;

    @Column(name = "rate_cd", nullable = false)
    private String rateCd;

    @Column(name = "rate_nm", nullable = false)
    private String rateNm;

    @Column(name = "ccy_cd", nullable = false)
    private String ccyCd;

    @Column(name = "rate_pct", nullable = false, precision = 9, scale = 5)
    private BigDecimal ratePct;

    @Column(name = "eff_dt", nullable = false)
    private LocalDate effDt;

    @Column(name = "rate_sts", nullable = false)
    private String rateSts;

    @Column(name = "crt_dt", nullable = false, updatable = false)
    private OffsetDateTime crtDt;

    @Column(name = "crt_by", nullable = false, updatable = false)
    private String crtBy;

    @PrePersist
    public void prePersist() {
        crtDt = OffsetDateTime.now();
    }

    public Long getBaseRateId() { return baseRateId; }
    public void setBaseRateId(Long value) { baseRateId = value; }

    public String getRateCd() { return rateCd; }
    public void setRateCd(String value) { rateCd = value; }

    public String getRateNm() { return rateNm; }
    public void setRateNm(String value) { rateNm = value; }

    public String getCcyCd() { return ccyCd; }
    public void setCcyCd(String value) { ccyCd = value; }

    public BigDecimal getRatePct() { return ratePct; }
    public void setRatePct(BigDecimal value) { ratePct = value; }

    public LocalDate getEffDt() { return effDt; }
    public void setEffDt(LocalDate value) { effDt = value; }

    public String getRateSts() { return rateSts; }
    public void setRateSts(String value) { rateSts = value; }

    public OffsetDateTime getCrtDt() { return crtDt; }
    public void setCrtDt(OffsetDateTime value) { crtDt = value; }

    public String getCrtBy() { return crtBy; }
    public void setCrtBy(String value) { crtBy = value; }
}