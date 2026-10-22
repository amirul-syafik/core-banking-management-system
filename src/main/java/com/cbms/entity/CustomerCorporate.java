package com.cbms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_corporate")
public class CustomerCorporate {

    @Id
    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "reg_no", length = 100)
    private String regNo;

    @Column(name = "reg_cty", length = 2)
    private String regCty;

    @Column(name = "inc_dt")
    private LocalDate incDt;

    @Column(name = "biz_typ", length = 100)
    private String bizTyp;

    @Column(name = "crt_dt", nullable = false)
    private OffsetDateTime crtDt;

    @Column(name = "crt_by", nullable = false, length = 100)
    private String crtBy;

    @Column(name = "upd_dt", nullable = false)
    private OffsetDateTime updDt;

    @Column(name = "upd_by", length = 100)
    private String updBy;

    public CustomerCorporate() {
    }

    public Long getCustId() {
        return custId;
    }

    public void setCustId(Long custId) {
        this.custId = custId;
    }

    public String getRegNo() {
        return regNo;
    }

    public void setRegNo(String regNo) {
        this.regNo = regNo;
    }

    public String getRegCty() {
        return regCty;
    }

    public void setRegCty(String regCty) {
        this.regCty = regCty;
    }

    public LocalDate getIncDt() {
        return incDt;
    }

    public void setIncDt(LocalDate incDt) {
        this.incDt = incDt;
    }

    public String getBizTyp() {
        return bizTyp;
    }

    public void setBizTyp(String bizTyp) {
        this.bizTyp = bizTyp;
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

