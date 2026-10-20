package com.cbms.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "application_activity_audit", indexes = {
        @Index(name = "ix_activity_audit_created", columnList = "created_at"),
        @Index(name = "ix_activity_audit_actor_created", columnList = "actor,created_at")
})
public class ApplicationActivityAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;
    @Column(name = "actor", nullable = false, length = 100)
    private String actor;
    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;
    @Column(name = "request_path", nullable = false, length = 500)
    private String requestPath;
    @Column(name = "http_status", nullable = false)
    private Integer httpStatus;
    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getAuditId() {
        return auditId;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String v) {
        actor = v;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String v) {
        httpMethod = v;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String v) {
        requestPath = v;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer v) {
        httpStatus = v;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String v) {
        outcome = v;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime v) {
        createdAt = v;
    }
}
