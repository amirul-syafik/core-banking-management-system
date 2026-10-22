package com.cbms.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_identification")
public class CustomerIdentification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idn_id")
    private Long idnId;

    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "doc_id")
    private Long docId;

    @Column(name = "doc_typ", nullable = false, length = 30)
    private String docTyp;

    @Column(name = "doc_no", nullable = false, length = 100)
    private String docNo;

    @Column(name = "iss_cty", nullable = false, length = 2)
    private String issCty;

    @Column(name = "iss_dt")
    private LocalDate issDt;

    @Column(name = "exp_dt")
    private LocalDate expDt;

    @Column(name = "pri_flg", nullable = false)
    private Boolean priFlg = false;

    @Column(name = "crt_dt", nullable = false)
    private OffsetDateTime crtDt;

    @Column(name = "crt_by", nullable = false, length = 100)
    private String crtBy;

    @Column(name = "upd_dt", nullable = false)
    private OffsetDateTime updDt;

    @Column(name = "upd_by", length = 100)
    private String updBy;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (crtDt == null) crtDt = now;
        if (updDt == null) updDt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updDt = OffsetDateTime.now();
    }

    public Long getIdnId() {
        return idnId;
    }

    public void setIdnId(Long idnId) {
        this.idnId = idnId;
    }

    public Long getCustId() {
        return custId;
    }

    public void setCustId(Long custId) {
        this.custId = custId;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public String getDocTyp() {
        return docTyp;
    }

    public void setDocTyp(String docTyp) {
        this.docTyp = docTyp;
    }

    public String getDocNo() {
        return docNo;
    }

    public void setDocNo(String docNo) {
        this.docNo = docNo;
    }

    public String getIssCty() {
        return issCty;
    }

    public void setIssCty(String issCty) {
        this.issCty = issCty;
    }

    public LocalDate getIssDt() {
        return issDt;
    }

    public void setIssDt(LocalDate issDt) {
        this.issDt = issDt;
    }

    public LocalDate getExpDt() {
        return expDt;
    }

    public void setExpDt(LocalDate expDt) {
        this.expDt = expDt;
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
