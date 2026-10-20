package com.cbms.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_collateral_valuation", schema = "public")
public class BankCollateralValuation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "valuation_id")
    private Long valuationId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collateral_id", nullable = false)
    private BankCollateral collateral;
    @Column(name = "valuation_typ", nullable = false, length = 20)
    private String valuationTyp;
    @Column(name = "valuation_dt", nullable = false)
    private LocalDate valuationDt;
    @Column(name = "market_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal marketValue;
    @Column(name = "forced_sale_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal forcedSaleValue;
    @Column(name = "currency_cd", nullable = false, length = 3)
    private String currencyCd;
    @Column(name = "valuation_source", nullable = false, length = 150)
    private String valuationSource;
    @Column(name = "remarks", length = 500)
    private String remarks;
    @Column(name = "crt_dt", nullable = false, updatable = false)
    private OffsetDateTime crtDt;
    @Column(name = "crt_by", nullable = false, updatable = false, length = 100)
    private String crtBy;

    public Long getValuationId() {
        return valuationId;
    }

    public BankCollateral getCollateral() {
        return collateral;
    }

    public void setCollateral(BankCollateral v) {
        collateral = v;
    }

    public String getValuationTyp() {
        return valuationTyp;
    }

    public void setValuationTyp(String v) {
        valuationTyp = v;
    }

    public LocalDate getValuationDt() {
        return valuationDt;
    }

    public void setValuationDt(LocalDate v) {
        valuationDt = v;
    }

    public BigDecimal getMarketValue() {
        return marketValue;
    }

    public void setMarketValue(BigDecimal v) {
        marketValue = v;
    }

    public BigDecimal getForcedSaleValue() {
        return forcedSaleValue;
    }

    public void setForcedSaleValue(BigDecimal v) {
        forcedSaleValue = v;
    }

    public String getCurrencyCd() {
        return currencyCd;
    }

    public void setCurrencyCd(String v) {
        currencyCd = v;
    }

    public String getValuationSource() {
        return valuationSource;
    }

    public void setValuationSource(String v) {
        valuationSource = v;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String v) {
        remarks = v;
    }

    public OffsetDateTime getCrtDt() {
        return crtDt;
    }

    public void setCrtDt(OffsetDateTime v) {
        crtDt = v;
    }

    public String getCrtBy() {
        return crtBy;
    }

    public void setCrtBy(String v) {
        crtBy = v;
    }
}
