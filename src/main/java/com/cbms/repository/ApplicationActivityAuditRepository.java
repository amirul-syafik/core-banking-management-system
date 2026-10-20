package com.cbms.repository;

import com.cbms.entity.ApplicationActivityAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ApplicationActivityAuditRepository extends JpaRepository<ApplicationActivityAudit, Long>, JpaSpecificationExecutor<ApplicationActivityAudit> {
}
