package com.cbms.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_status_request")
public class CustomerStatusRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;
    @Column(name = "cust_id", nullable = false)
    private Long custId;
    @Column(name = "from_status", nullable = false, length = 15)
    private String fromStatus;
    @Column(name = "to_status", nullable = false, length = 15)
    private String toStatus;
    @Column(name = "request_status", nullable = false, length = 15)
    private String requestStatus;
    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;
    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;
    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;
    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    public Long getRequestId() {
        return requestId;
    }

    public Long getCustId() {
        return custId;
    }

    public void setCustId(Long v) {
        custId = v;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String v) {
        fromStatus = v;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String v) {
        toStatus = v;
    }

    public String getRequestStatus() {
        return requestStatus;
    }

    public void setRequestStatus(String v) {
        requestStatus = v;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String v) {
        requestedBy = v;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(OffsetDateTime v) {
        requestedAt = v;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String v) {
        reviewedBy = v;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime v) {
        reviewedAt = v;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String v) {
        reason = v;
    }

    public Long getRowVersion() {
        return rowVersion;
    }
}
