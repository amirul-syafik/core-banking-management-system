package com.cbms.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_financing_collateral", schema = "public")
public class BankFinancingCollateral {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "link_id")
    private Long linkId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appl_id")
    private BankFinancingApplication application;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fac_id")
    private BankFinancingFacility facility;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collateral_id")
    private BankCollateral collateral;
    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal allocatedAmount;
    @Column(name = "link_sts", nullable = false, length = 20)
    private String linkSts;
    @Column(name = "linked_dt", nullable = false)
    private OffsetDateTime linkedDt;
    @Column(name = "activated_dt")
    private OffsetDateTime activatedDt;
    @Column(name = "released_dt")
    private OffsetDateTime releasedDt;
    @Column(name = "remarks", length = 500)
    private String remarks;
    @Column(name = "crt_by", nullable = false, length = 100)
    private String crtBy;
    @Column(name = "upd_dt", nullable = false)
    private OffsetDateTime updDt;
    @Column(name = "upd_by", length = 100)
    private String updBy;

    public Long getLinkId() {
        return linkId;
    }

    public BankFinancingApplication getApplication() {
        return application;
    }

    public void setApplication(BankFinancingApplication v) {
        application = v;
    }

    public BankFinancingFacility getFacility() {
        return facility;
    }

    public void setFacility(BankFinancingFacility v) {
        facility = v;
    }

    public BankCollateral getCollateral() {
        return collateral;
    }

    public void setCollateral(BankCollateral v) {
        collateral = v;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount;
    }

    public void setAllocatedAmount(BigDecimal v) {
        allocatedAmount = v;
    }

    public String getLinkSts() {
        return linkSts;
    }

    public void setLinkSts(String v) {
        linkSts = v;
    }

    public OffsetDateTime getLinkedDt() {
        return linkedDt;
    }

    public void setLinkedDt(OffsetDateTime v) {
        linkedDt = v;
    }

    public OffsetDateTime getActivatedDt() {
        return activatedDt;
    }

    public void setActivatedDt(OffsetDateTime v) {
        activatedDt = v;
    }

    public OffsetDateTime getReleasedDt() {
        return releasedDt;
    }

    public void setReleasedDt(OffsetDateTime v) {
        releasedDt = v;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String v) {
        remarks = v;
    }

    public String getCrtBy() {
        return crtBy;
    }

    public void setCrtBy(String v) {
        crtBy = v;
    }

    public OffsetDateTime getUpdDt() {
        return updDt;
    }

    public void setUpdDt(OffsetDateTime v) {
        updDt = v;
    }

    public String getUpdBy() {
        return updBy;
    }

    public void setUpdBy(String v) {
        updBy = v;
    }
}
