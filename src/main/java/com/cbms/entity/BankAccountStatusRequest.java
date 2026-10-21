package com.cbms.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "bank_account_status_request")
public class BankAccountStatusRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requestId;
    @Column(nullable = false)
    private Long acctId;
    @Column(nullable = false, length = 20)
    private String fromStatus;
    @Column(nullable = false, length = 20)
    private String toStatus;
    @Column(nullable = false, length = 20)
    private String requestStatus;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(nullable = false, length = 100)
    private String requestedBy;
    @Column(nullable = false)
    private OffsetDateTime requestedAt;
    @Column(length = 100)
    private String reviewedBy;
    private OffsetDateTime reviewedAt;
    @Version
    private Long rowVersion;

    public Long getRequestId() {
        return requestId;
    }

    public Long getAcctId() {
        return acctId;
    }

    public void setAcctId(Long x) {
        acctId = x;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String x) {
        fromStatus = x;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String x) {
        toStatus = x;
    }

    public String getRequestStatus() {
        return requestStatus;
    }

    public void setRequestStatus(String x) {
        requestStatus = x;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String x) {
        reason = x;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String x) {
        requestedBy = x;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(OffsetDateTime x) {
        requestedAt = x;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String x) {
        reviewedBy = x;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime x) {
        reviewedAt = x;
    }
}
