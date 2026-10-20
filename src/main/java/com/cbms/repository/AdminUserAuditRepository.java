package com.cbms.repository;

import com.cbms.entity.AdminUserAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminUserAuditRepository extends JpaRepository<AdminUserAudit, Long> {
    List<AdminUserAudit> findByTargetUserIdOrderByCreatedAtDesc(Long targetUserId);
}
