package com.cbms.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_ledger_entry")
public class BankLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entry_id")
    private Long entryId;

    @Column(name = "txn_id", nullable = false)
    private Long txnId;

    @Column(name = "gl_id", nullable = false)
    private Long glId;

    @Column(name = "acct_id")
    private Long acctId;

    @Column(name = "entry_side", nullable = false, length = 6)
    private String entrySide;

    @Column(name = "entry_amt", nullable = false, precision = 19, scale = 2)
    private BigDecimal entryAmt;

    @Column(name = "crt_dt", nullable = false)
    private OffsetDateTime crtDt;

    public Long getEntryId() {
        return entryId;
    }

    public void setEntryId(Long entryId) {
        this.entryId = entryId;
    }

    public Long getTxnId() {
        return txnId;
    }

    public void setTxnId(Long txnId) {
        this.txnId = txnId;
    }

    public Long getGlId() {
        return glId;
    }

    public void setGlId(Long glId) {
        this.glId = glId;
    }

    public Long getAcctId() {
        return acctId;
    }

    public void setAcctId(Long acctId) {
        this.acctId = acctId;
    }

    public String getEntrySide() {
        return entrySide;
    }

    public void setEntrySide(String entrySide) {
        this.entrySide = entrySide;
    }

    public BigDecimal getEntryAmt() {
        return entryAmt;
    }

    public void setEntryAmt(BigDecimal entryAmt) {
        this.entryAmt = entryAmt;
    }

    public OffsetDateTime getCrtDt() {
        return crtDt;
    }

    public void setCrtDt(OffsetDateTime crtDt) {
        this.crtDt = crtDt;
    }
}
