package com.cbms.repository;

import com.cbms.entity.BankSchemeAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankSchemeAuditRepository
        extends JpaRepository<BankSchemeAudit, Long> {

    List<BankSchemeAudit>
    findBySchmIdOrderByActionDtDescAuditIdDesc(Long schmId);
}