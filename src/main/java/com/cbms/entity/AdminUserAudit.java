package com.cbms.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "app_user_admin_audit")
public class AdminUserAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;
    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;
    @Column(name = "actor", nullable = false, length = 100)
    private String actor;
    @Column(name = "action", nullable = false, length = 40)
    private String action;
    @Column(name = "details", length = 1000)
    private String details;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AdminUserAudit() {
    }

    public AdminUserAudit(Long id, String actor, String action, String details) {
        this.targetUserId = id;
        this.actor = actor;
        this.action = action;
        this.details = details;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getAuditId() {
        return auditId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
