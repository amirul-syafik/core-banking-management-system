package com.cbms.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_relationship")
public class CustomerRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rel_id")
    private Long relId;

    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "rel_cust_id", nullable = false)
    private Long relCustId;

    @Column(name = "rel_typ", length = 30)
    private String relTyp;

    @Column(name = "sts", length = 20)
    private String sts;

    @Column(name = "eff_dt")
    private LocalDate effDt;

    @Column(name = "end_dt")
    private LocalDate endDt;

    @Column(name = "crt_dt")
    private OffsetDateTime crtDt;

    @Column(name = "crt_by")
    private String crtBy;

    @Column(name = "upd_dt")
    private OffsetDateTime updDt;

    @Column(name = "upd_by")
    private String updBy;

    public CustomerRelationship() {
    }

    public Long getRelId() {
        return relId;
    }

    public void setRelId(Long relId) {
        this.relId = relId;
    }

    public Long getCustId() {
        return custId;
    }

    public void setCustId(Long custId) {
        this.custId = custId;
    }

    public Long getRelCustId() {
        return relCustId;
    }

    public void setRelCustId(Long relCustId) {
        this.relCustId = relCustId;
    }

    public String getRelTyp() {
        return relTyp;
    }

    public void setRelTyp(String relTyp) {
        this.relTyp = relTyp;
    }

    public String getSts() {
        return sts;
    }

    public void setSts(String sts) {
        this.sts = sts;
    }

    public LocalDate getEffDt() {
        return effDt;
    }

    public void setEffDt(LocalDate effDt) {
        this.effDt = effDt;
    }

    public LocalDate getEndDt() {
        return endDt;
    }

    public void setEndDt(LocalDate endDt) {
        this.endDt = endDt;
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