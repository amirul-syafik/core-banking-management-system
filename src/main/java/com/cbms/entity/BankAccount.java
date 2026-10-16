package com.cbms.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_account")
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "acct_id")
    private Long acctId;

    @Column(name = "cust_id", nullable = false)
    private Long custId;

    @Column(name = "acct_no", nullable = false, length = 30)
    private String acctNo;

    @Column(name = "acct_typ", nullable = false, length = 20)
    private String acctTyp;

    @Column(name = "ccy_cd", nullable = false, length = 3)
    private String ccyCd;

    @Column(name = "acct_sts", nullable = false, length = 20)
    private String acctSts;

    @Column(name = "open_dt", nullable = false)
    private LocalDate openDt;

    @Column(name = "close_dt")
    private LocalDate closeDt;

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

        if (openDt == null) openDt = LocalDate.now();
        if (crtDt == null) crtDt = now;
        if (updDt == null) updDt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updDt = OffsetDateTime.now();
    }

    public Long getAcctId() { return acctId; }
    public void setAcctId(Long acctId) { this.acctId = acctId; }

    public Long getCustId() { return custId; }
    public void setCustId(Long custId) { this.custId = custId; }

    public String getAcctNo() { return acctNo; }
    public void setAcctNo(String acctNo) { this.acctNo = acctNo; }

    public String getAcctTyp() { return acctTyp; }
    public void setAcctTyp(String acctTyp) { this.acctTyp = acctTyp; }

    public String getCcyCd() { return ccyCd; }
    public void setCcyCd(String ccyCd) { this.ccyCd = ccyCd; }

    public String getAcctSts() { return acctSts; }
    public void setAcctSts(String acctSts) { this.acctSts = acctSts; }

    public LocalDate getOpenDt() { return openDt; }
    public void setOpenDt(LocalDate openDt) { this.openDt = openDt; }

    public LocalDate getCloseDt() { return closeDt; }
    public void setCloseDt(LocalDate closeDt) { this.closeDt = closeDt; }

    public OffsetDateTime getCrtDt() { return crtDt; }
    public void setCrtDt(OffsetDateTime crtDt) { this.crtDt = crtDt; }

    public String getCrtBy() { return crtBy; }
    public void setCrtBy(String crtBy) { this.crtBy = crtBy; }

    public OffsetDateTime getUpdDt() { return updDt; }
    public void setUpdDt(OffsetDateTime updDt) { this.updDt = updDt; }

    public String getUpdBy() { return updBy; }
    public void setUpdBy(String updBy) { this.updBy = updBy; }
}
