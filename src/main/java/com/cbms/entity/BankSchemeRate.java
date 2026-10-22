package com.cbms.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_scheme_rate", schema = "public")
public class BankSchemeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schm_rate_id")
    private Long schmRateId;

    @Column(name = "schm_id", nullable = false)
    private Long schmId;

    @Column(name = "rate_typ", nullable = false)
    private String rateTyp;

    @Column(name = "base_rate_cd")
    private String baseRateCd;

    @Column(name = "fixed_rate_pct", precision = 9, scale = 5)
    private BigDecimal fixedRatePct;

    @Column(name = "margin_pct", precision = 9, scale = 5)
    private BigDecimal marginPct;

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

    public Long getSchmRateId() {
        return schmRateId;
    }

    public void setSchmRateId(Long value) {
        schmRateId = value;
    }

    public Long getSchmId() {
        return schmId;
    }

    public void setSchmId(Long value) {
        schmId = value;
    }

    public String getRateTyp() {
        return rateTyp;
    }

    public void setRateTyp(String value) {
        rateTyp = value;
    }

    public String getBaseRateCd() {
        return baseRateCd;
    }

    public void setBaseRateCd(String value) {
        baseRateCd = value;
    }

    public BigDecimal getFixedRatePct() {
        return fixedRatePct;
    }

    public void setFixedRatePct(BigDecimal value) {
        fixedRatePct = value;
    }

    public BigDecimal getMarginPct() {
        return marginPct;
    }

    public void setMarginPct(BigDecimal value) {
        marginPct = value;
    }

    public LocalDate getEffDt() {
        return effDt;
    }

    public void setEffDt(LocalDate value) {
        effDt = value;
    }

    public String getRateSts() {
        return rateSts;
    }

    public void setRateSts(String value) {
        rateSts = value;
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
}