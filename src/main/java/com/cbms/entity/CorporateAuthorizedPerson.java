package com.cbms.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "corporate_authorized_person")
public class CorporateAuthorizedPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "aut_id")
    private Long autId;

    @Column(name = "corp_cust_id", nullable = false)
    private Long corpCustId;

    @Column(name = "auth_cust_id", nullable = false)
    private Long authCustId;

    @Column(name = "auth_typ", nullable = false, length = 30)
    private String authTyp;

    @Column(name = "auth_ref", length = 100)
    private String authRef;

    @Column(name = "eff_dt", nullable = false)
    private LocalDate effDt;

    @Column(name = "end_dt")
    private LocalDate endDt;

    @Column(name = "sts", nullable = false, length = 20)
    private String sts;

    @Column(name = "rmk", length = 1000)
    private String rmk;

    @Column(name = "crt_dt", nullable = false)
    private OffsetDateTime crtDt;

    @Column(name = "crt_by", nullable = false, length = 50)
    private String crtBy;

    @Column(name = "upd_dt")
    private OffsetDateTime updDt;

    @Column(name = "upd_by", length = 50)
    private String updBy;

    public CorporateAuthorizedPerson() {
    }

    public Long getAutId() {
        return autId;
    }

    public Long getCorpCustId() {
        return corpCustId;
    }

    public void setCorpCustId(Long corpCustId) {
        this.corpCustId = corpCustId;
    }

    public Long getAuthCustId() {
        return authCustId;
    }

    public void setAuthCustId(Long authCustId) {
        this.authCustId = authCustId;
    }

    public String getAuthTyp() {
        return authTyp;
    }

    public void setAuthTyp(String authTyp) {
        this.authTyp = authTyp;
    }

    public String getAuthRef() {
        return authRef;
    }

    public void setAuthRef(String authRef) {
        this.authRef = authRef;
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

    public String getSts() {
        return sts;
    }

    public void setSts(String sts) {
        this.sts = sts;
    }

    public String getRmk() {
        return rmk;
    }

    public void setRmk(String rmk) {
        this.rmk = rmk;
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
