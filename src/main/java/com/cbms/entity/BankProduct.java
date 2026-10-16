package com.cbms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_product", schema = "public")
public class BankProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prod_id")
    private Long prodId;

    @Column(name = "prod_cd", nullable = false, length = 30)
    private String prodCd;

    @Column(name = "prod_nm", nullable = false, length = 150)
    private String prodNm;

    @Column(name = "prod_cat", nullable = false, length = 30)
    private String prodCat;

    @Column(name = "banking_typ", nullable = false, length = 20)
    private String bankingTyp;

    @Column(name = "ccy_cd", nullable = false, length = 3)
    private String ccyCd;

    @Column(name = "prod_desc", length = 500)
    private String prodDesc;

    @Column(name = "prod_sts", nullable = false, length = 20)
    private String prodSts;

    @Column(name = "crt_dt", nullable = false, updatable = false)
    private OffsetDateTime crtDt;

    @Column(name = "crt_by", nullable = false, updatable = false)
    private String crtBy;

    @Column(name = "upd_dt", nullable = false)
    private OffsetDateTime updDt;

    @Column(name = "upd_by")
    private String updBy;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        crtDt = now;
        updDt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updDt = OffsetDateTime.now();
    }

    public Long getProdId() { return prodId; }
    public void setProdId(Long prodId) { this.prodId = prodId; }

    public String getProdCd() { return prodCd; }
    public void setProdCd(String prodCd) { this.prodCd = prodCd; }

    public String getProdNm() { return prodNm; }
    public void setProdNm(String prodNm) { this.prodNm = prodNm; }

    public String getProdCat() { return prodCat; }
    public void setProdCat(String prodCat) { this.prodCat = prodCat; }

    public String getBankingTyp() { return bankingTyp; }
    public void setBankingTyp(String bankingTyp) { this.bankingTyp = bankingTyp; }

    public String getCcyCd() { return ccyCd; }
    public void setCcyCd(String ccyCd) { this.ccyCd = ccyCd; }

    public String getProdDesc() { return prodDesc; }
    public void setProdDesc(String prodDesc) { this.prodDesc = prodDesc; }

    public String getProdSts() { return prodSts; }
    public void setProdSts(String prodSts) { this.prodSts = prodSts; }

    public OffsetDateTime getCrtDt() { return crtDt; }
    public void setCrtDt(OffsetDateTime crtDt) { this.crtDt = crtDt; }

    public String getCrtBy() { return crtBy; }
    public void setCrtBy(String crtBy) { this.crtBy = crtBy; }

    public OffsetDateTime getUpdDt() { return updDt; }
    public void setUpdDt(OffsetDateTime updDt) { this.updDt = updDt; }

    public String getUpdBy() { return updBy; }
    public void setUpdBy(String updBy) { this.updBy = updBy; }
}
