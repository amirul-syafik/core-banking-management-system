package com.cbms.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_collateral", schema = "public")
public class BankCollateral {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "collateral_id")
    private Long collateralId;
    @Column(name = "collateral_ref", nullable = false, length = 40)
    private String collateralRef;
    @Column(name = "collateral_typ", nullable = false, length = 30)
    private String collateralTyp;
    @Column(name = "collateral_desc", nullable = false, length = 500)
    private String collateralDesc;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_cust_id")
    private Customer owner;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private BankVehicle vehicle;
    @Column(name = "collateral_sts", nullable = false, length = 20)
    private String collateralSts;
    @Column(name = "crt_dt", nullable = false, updatable = false)
    private OffsetDateTime crtDt;
    @Column(name = "crt_by", nullable = false, updatable = false, length = 100)
    private String crtBy;
    @Column(name = "upd_dt", nullable = false)
    private OffsetDateTime updDt;
    @Column(name = "upd_by", length = 100)
    private String updBy;

    public Long getCollateralId() {
        return collateralId;
    }

    public String getCollateralRef() {
        return collateralRef;
    }

    public void setCollateralRef(String x) {
        collateralRef = x;
    }

    public String getCollateralTyp() {
        return collateralTyp;
    }

    public void setCollateralTyp(String x) {
        collateralTyp = x;
    }

    public String getCollateralDesc() {
        return collateralDesc;
    }

    public void setCollateralDesc(String x) {
        collateralDesc = x;
    }

    public Customer getOwner() {
        return owner;
    }

    public void setOwner(Customer x) {
        owner = x;
    }

    public BankVehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(BankVehicle x) {
        vehicle = x;
    }

    public String getCollateralSts() {
        return collateralSts;
    }

    public void setCollateralSts(String x) {
        collateralSts = x;
    }

    public OffsetDateTime getCrtDt() {
        return crtDt;
    }

    public void setCrtDt(OffsetDateTime x) {
        crtDt = x;
    }

    public String getCrtBy() {
        return crtBy;
    }

    public void setCrtBy(String x) {
        crtBy = x;
    }

    public OffsetDateTime getUpdDt() {
        return updDt;
    }

    public void setUpdDt(OffsetDateTime x) {
        updDt = x;
    }

    public String getUpdBy() {
        return updBy;
    }

    public void setUpdBy(String x) {
        updBy = x;
    }
}
