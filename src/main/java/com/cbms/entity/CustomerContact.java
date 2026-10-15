package com.cbms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_contact")
public class CustomerContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "con_id")
    private Long conId;

    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "con_typ", length = 20)
    private String conTyp;

    @Column(name = "con_val", length = 200)
    private String conVal;

    @Column(name = "pri_flg")
    private Boolean priFlg;

    @Column(name = "act_flg")
    private Boolean actFlg;

    @Column(name = "vfy_dt")
    private OffsetDateTime vfyDt;

    @Column(name = "crt_dt")
    private OffsetDateTime crtDt;

    @Column(name = "crt_by")
    private String crtBy;

    @Column(name = "upd_dt")
    private OffsetDateTime updDt;

    @Column(name = "upd_by")
    private String updBy;

    public CustomerContact() {
    }

    public Long getConId() {
        return conId;
    }

    public void setConId(Long conId) {
        this.conId = conId;
    }

    public Long getCustId() {
        return custId;
    }

    public void setCustId(Long custId) {
        this.custId = custId;
    }

    public String getConTyp() {
        return conTyp;
    }

    public void setConTyp(String conTyp) {
        this.conTyp = conTyp;
    }

    public String getConVal() {
        return conVal;
    }

    public void setConVal(String conVal) {
        this.conVal = conVal;
    }

    public Boolean getPriFlg() {
        return priFlg;
    }

    public void setPriFlg(Boolean priFlg) {
        this.priFlg = priFlg;
    }

    public Boolean getActFlg() {
        return actFlg;
    }

    public void setActFlg(Boolean actFlg) {
        this.actFlg = actFlg;
    }

    public OffsetDateTime getVfyDt() {
        return vfyDt;
    }

    public void setVfyDt(OffsetDateTime vfyDt) {
        this.vfyDt = vfyDt;
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