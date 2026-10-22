package com.cbms.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "bank_ledger_account")
public class BankLedgerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gl_id")
    private Long glId;

    @Column(name = "gl_cd", nullable = false, length = 30)
    private String glCd;

    @Column(name = "gl_nm", nullable = false, length = 150)
    private String glNm;

    @Column(name = "gl_typ", nullable = false, length = 20)
    private String glTyp;

    @Column(name = "ccy_cd", nullable = false, length = 3)
    private String ccyCd;

    @Column(name = "act_flg", nullable = false)
    private Boolean actFlg;

    public Long getGlId() {
        return glId;
    }

    public void setGlId(Long glId) {
        this.glId = glId;
    }

    public String getGlCd() {
        return glCd;
    }

    public void setGlCd(String glCd) {
        this.glCd = glCd;
    }

    public String getGlNm() {
        return glNm;
    }

    public void setGlNm(String glNm) {
        this.glNm = glNm;
    }

    public String getGlTyp() {
        return glTyp;
    }

    public void setGlTyp(String glTyp) {
        this.glTyp = glTyp;
    }

    public String getCcyCd() {
        return ccyCd;
    }

    public void setCcyCd(String ccyCd) {
        this.ccyCd = ccyCd;
    }

    public Boolean getActFlg() {
        return actFlg;
    }

    public void setActFlg(Boolean actFlg) {
        this.actFlg = actFlg;
    }
}
