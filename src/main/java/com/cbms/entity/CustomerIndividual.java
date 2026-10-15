package com.cbms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_individual")
public class CustomerIndividual {

    @Id
    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "gndr", length = 20)
    private String gndr;

    @Column(name = "nat_cd", length = 2)
    private String natCd;

    @Column(name = "mrtl_sts", length = 20)
    private String mrtlSts;

    @Column(name = "crt_dt", nullable = false)
    private OffsetDateTime crtDt;

    @Column(name = "crt_by", nullable = false, length = 100)
    private String crtBy;

    @Column(name = "upd_dt", nullable = false)
    private OffsetDateTime updDt;

    @Column(name = "upd_by", length = 100)
    private String updBy;

    public CustomerIndividual() {
    }

    public Long getCustId() {
        return custId;
    }

    public void setCustId(Long custId) {
        this.custId = custId;
    }

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public String getGndr() {
        return gndr;
    }

    public void setGndr(String gndr) {
        this.gndr = gndr;
    }

    public String getNatCd() {
        return natCd;
    }

    public void setNatCd(String natCd) {
        this.natCd = natCd;
    }

    public String getMrtlSts() {
        return mrtlSts;
    }

    public void setMrtlSts(String mrtlSts) {
        this.mrtlSts = mrtlSts;
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
