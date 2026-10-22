package com.cbms.repository;

import com.cbms.entity.BankProductAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankProductAuditRepository
        extends JpaRepository<BankProductAudit, Long> {

    List<BankProductAudit>
    findByProdIdOrderByActionDtDescAuditIdDesc(Long prodId);
}