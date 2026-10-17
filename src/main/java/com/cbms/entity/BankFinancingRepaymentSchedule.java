package com.cbms.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_financing_repayment_schedule", schema = "public")
public class BankFinancingRepaymentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sched_id")
    private Long schedId;

    @Column(name = "fac_id", nullable = false)
    private Long facId;

    @Column(name = "inst_no", nullable = false)
    private Integer instNo;

    @Column(name = "due_dt", nullable = false)
    private LocalDate dueDt;

    @Column(name = "opening_principal", nullable = false)
    private BigDecimal openingPrincipal;

    @Column(name = "principal_amt", nullable = false)
    private BigDecimal principalAmt;

    @Column(name = "interest_amt", nullable = false)
    private BigDecimal interestAmt;

    @Column(name = "inst_amt", nullable = false)
    private BigDecimal instAmt;

    @Column(name = "closing_principal", nullable = false)
    private BigDecimal closingPrincipal;

    @Column(name = "paid_principal", nullable = false)
    private BigDecimal paidPrincipal = BigDecimal.ZERO;

    @Column(name = "paid_interest", nullable = false)
    private BigDecimal paidInterest = BigDecimal.ZERO;

    @Column(name = "inst_sts", nullable = false)
    private String instSts = "PENDING";

    @Column(name = "crt_dt", nullable = false)
    private OffsetDateTime crtDt;

    @Column(name = "crt_by", nullable = false)
    private String crtBy;

    @PrePersist
    public void prePersist() {
        if (crtDt == null) crtDt = OffsetDateTime.now();
    }

    public Long getSchedId() { return schedId; }
    public void setSchedId(Long v) { schedId = v; }

    public Long getFacId() { return facId; }
    public void setFacId(Long v) { facId = v; }

    public Integer getInstNo() { return instNo; }
    public void setInstNo(Integer v) { instNo = v; }

    public LocalDate getDueDt() { return dueDt; }
    public void setDueDt(LocalDate v) { dueDt = v; }

    public BigDecimal getOpeningPrincipal() { return openingPrincipal; }
    public void setOpeningPrincipal(BigDecimal v) { openingPrincipal = v; }

    public BigDecimal getPrincipalAmt() { return principalAmt; }
    public void setPrincipalAmt(BigDecimal v) { principalAmt = v; }

    public BigDecimal getInterestAmt() { return interestAmt; }
    public void setInterestAmt(BigDecimal v) { interestAmt = v; }

    public BigDecimal getInstAmt() { return instAmt; }
    public void setInstAmt(BigDecimal v) { instAmt = v; }

    public BigDecimal getClosingPrincipal() { return closingPrincipal; }
    public void setClosingPrincipal(BigDecimal v) { closingPrincipal = v; }

    public BigDecimal getPaidPrincipal() { return paidPrincipal; }
    public void setPaidPrincipal(BigDecimal v) { paidPrincipal = v; }

    public BigDecimal getPaidInterest() { return paidInterest; }
    public void setPaidInterest(BigDecimal v) { paidInterest = v; }

    public String getInstSts() { return instSts; }
    public void setInstSts(String v) { instSts = v; }

    public OffsetDateTime getCrtDt() { return crtDt; }
    public void setCrtDt(OffsetDateTime v) { crtDt = v; }

    public String getCrtBy() { return crtBy; }
    public void setCrtBy(String v) { crtBy = v; }
}
