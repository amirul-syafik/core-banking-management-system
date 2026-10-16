package com.cbms.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_account_balance")
public class BankAccountBalance {

    @Id
    @Column(name = "acct_id")
    private Long acctId;

    @Column(
            name = "ledger_bal",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal ledgerBal;

    @Column(
            name = "avail_bal",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal availBal;

    @Column(name = "upd_dt", nullable = false)
    private OffsetDateTime updDt;

    @Column(name = "upd_by", nullable = false, length = 100)
    private String updBy;

    public Long getAcctId() {
        return acctId;
    }

    public void setAcctId(Long acctId) {
        this.acctId = acctId;
    }

    public BigDecimal getLedgerBal() {
        return ledgerBal;
    }

    public void setLedgerBal(BigDecimal ledgerBal) {
        this.ledgerBal = ledgerBal;
    }

    public BigDecimal getAvailBal() {
        return availBal;
    }

    public void setAvailBal(BigDecimal availBal) {
        this.availBal = availBal;
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
