package com.cbms.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_employment")
public class CustomerEmployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "emp_id")
    private Long empId;

    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "emp_typ", nullable = false, length = 30)
    private String empTyp;

    @Column(name = "emp_sts", nullable = false, length = 20)
    private String empSts;

    @Column(name = "emp_nm", length = 200)
    private String empNm;

    @Column(name = "occ", length = 100)
    private String occ;

    @Column(name = "ind_cd", length = 20)
    private String indCd;

    @Column(name = "start_dt")
    private LocalDate startDt;

    @Column(name = "end_dt")
    private LocalDate endDt;

    @Column(name = "mth_inc", precision = 18, scale = 2)
    private BigDecimal mthInc;

    @Column(name = "inc_ccy", nullable = false, length = 3)
    private String incCcy;

    @Column(name = "crt_dt", nullable = false)
    private OffsetDateTime crtDt;

    @Column(name = "crt_by", nullable = false, length = 50)
    private String crtBy;

    @Column(name = "upd_dt")
    private OffsetDateTime updDt;

    @Column(name = "upd_by", length = 50)
    private String updBy;

    public CustomerEmployment() {
    }

    public Long getEmpId() {
        return empId;
    }

    public Long getCustId() {
        return custId;
    }

    public void setCustId(Long custId) {
        this.custId = custId;
    }

    public String getEmpTyp() {
        return empTyp;
    }

    public void setEmpTyp(String empTyp) {
        this.empTyp = empTyp;
    }

    public String getEmpSts() {
        return empSts;
    }

    public void setEmpSts(String empSts) {
        this.empSts = empSts;
    }

    public String getEmpNm() {
        return empNm;
    }

    public void setEmpNm(String empNm) {
        this.empNm = empNm;
    }

    public String getOcc() {
        return occ;
    }

    public void setOcc(String occ) {
        this.occ = occ;
    }

    public String getIndCd() {
        return indCd;
    }

    public void setIndCd(String indCd) {
        this.indCd = indCd;
    }

    public LocalDate getStartDt() {
        return startDt;
    }

    public void setStartDt(LocalDate startDt) {
        this.startDt = startDt;
    }

    public LocalDate getEndDt() {
        return endDt;
    }

    public void setEndDt(LocalDate endDt) {
        this.endDt = endDt;
    }

    public BigDecimal getMthInc() {
        return mthInc;
    }

    public void setMthInc(BigDecimal mthInc) {
        this.mthInc = mthInc;
    }

    public String getIncCcy() {
        return incCcy;
    }

    public void setIncCcy(String incCcy) {
        this.incCcy = incCcy;
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
