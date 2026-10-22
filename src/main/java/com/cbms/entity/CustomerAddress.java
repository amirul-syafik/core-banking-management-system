package com.cbms.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_address")
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "adr_id")
    private Long adrId;

    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "adr_typ", length = 30)
    private String adrTyp;

    @Column(name = "adr_ln1", length = 200)
    private String adrLn1;

    @Column(name = "adr_ln2", length = 200)
    private String adrLn2;

    @Column(name = "adr_ln3", length = 200)
    private String adrLn3;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "st_prov", length = 100)
    private String stProv;

    @Column(name = "pst_cd", length = 20)
    private String pstCd;

    @Column(name = "cty_cd", length = 2)
    private String ctyCd;

    @Column(name = "pri_flg")
    private Boolean priFlg;

    @Column(name = "crt_dt")
    private OffsetDateTime crtDt;

    @Column(name = "crt_by")
    private String crtBy;

    @Column(name = "upd_dt")
    private OffsetDateTime updDt;

    @Column(name = "upd_by")
    private String updBy;

    public CustomerAddress() {
    }

    public Long getAdrId() {
        return adrId;
    }

    public void setAdrId(Long adrId) {
        this.adrId = adrId;
    }

    public Long getCustId() {
        return custId;
    }

    public void setCustId(Long custId) {
        this.custId = custId;
    }

    public String getAdrTyp() {
        return adrTyp;
    }

    public void setAdrTyp(String adrTyp) {
        this.adrTyp = adrTyp;
    }

    public String getAdrLn1() {
        return adrLn1;
    }

    public void setAdrLn1(String adrLn1) {
        this.adrLn1 = adrLn1;
    }

    public String getAdrLn2() {
        return adrLn2;
    }

    public void setAdrLn2(String adrLn2) {
        this.adrLn2 = adrLn2;
    }

    public String getAdrLn3() {
        return adrLn3;
    }

    public void setAdrLn3(String adrLn3) {
        this.adrLn3 = adrLn3;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getStProv() {
        return stProv;
    }

    public void setStProv(String stProv) {
        this.stProv = stProv;
    }

    public String getPstCd() {
        return pstCd;
    }

    public void setPstCd(String pstCd) {
        this.pstCd = pstCd;
    }

    public String getCtyCd() {
        return ctyCd;
    }

    public void setCtyCd(String ctyCd) {
        this.ctyCd = ctyCd;
    }

    public Boolean getPriFlg() {
        return priFlg;
    }

    public void setPriFlg(Boolean priFlg) {
        this.priFlg = priFlg;
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