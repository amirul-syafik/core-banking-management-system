package com.cbms.repository;

import com.cbms.entity.CustomerAuditHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAuditHistoryRepository
        extends JpaRepository<CustomerAuditHistory, Long> {

    Page<CustomerAuditHistory> findByCustId(
            Long custId,
            Pageable pageable
    );

    Page<CustomerAuditHistory> findByCustIdAndActionTyp(
            Long custId,
            String actionTyp,
            Pageable pageable
    );
}
