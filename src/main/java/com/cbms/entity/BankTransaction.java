package com.cbms.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_transaction")
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "txn_id")
    private Long txnId;

    @Column(name = "txn_ref", nullable = false, length = 50)
    private String txnRef;

    @Column(name = "acct_id", nullable = false)
    private Long acctId;

    @Column(name = "txn_typ", nullable = false, length = 20)
    private String txnTyp;

    @Column(name = "txn_amt", nullable = false, precision = 19, scale = 2)
    private BigDecimal txnAmt;

    @Column(name = "ccy_cd", nullable = false, length = 3)
    private String ccyCd;

    @Column(name = "txn_sts", nullable = false, length = 20)
    private String txnSts;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "crt_dt", nullable = false)
    private OffsetDateTime crtDt;

    @Column(name = "crt_by", nullable = false, length = 100)
    private String crtBy;

    @Column(name = "dest_acct_id")
    private Long destAcctId;

    @Column(name = "reversal_of_txn_id")
    private Long reversalOfTxnId;

    public Long getTxnId() { return txnId; }
    public void setTxnId(Long txnId) { this.txnId = txnId; }

    public String getTxnRef() { return txnRef; }
    public void setTxnRef(String txnRef) { this.txnRef = txnRef; }

    public Long getAcctId() { return acctId; }
    public void setAcctId(Long acctId) { this.acctId = acctId; }

    public String getTxnTyp() { return txnTyp; }
    public void setTxnTyp(String txnTyp) { this.txnTyp = txnTyp; }

    public BigDecimal getTxnAmt() { return txnAmt; }
    public void setTxnAmt(BigDecimal txnAmt) { this.txnAmt = txnAmt; }

    public String getCcyCd() { return ccyCd; }
    public void setCcyCd(String ccyCd) { this.ccyCd = ccyCd; }

    public String getTxnSts() { return txnSts; }
    public void setTxnSts(String txnSts) { this.txnSts = txnSts; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public OffsetDateTime getCrtDt() { return crtDt; }
    public void setCrtDt(OffsetDateTime crtDt) { this.crtDt = crtDt; }

    public String getCrtBy() { return crtBy; }
    public void setCrtBy(String crtBy) { this.crtBy = crtBy; }

    public Long getDestAcctId() {
        return destAcctId;
    }

    public void setDestAcctId(Long destAcctId) {
        this.destAcctId = destAcctId;
    }

    public Long getReversalOfTxnId() {
        return reversalOfTxnId;
    }

    public void setReversalOfTxnId(Long reversalOfTxnId) {
        this.reversalOfTxnId = reversalOfTxnId;
    }
}
