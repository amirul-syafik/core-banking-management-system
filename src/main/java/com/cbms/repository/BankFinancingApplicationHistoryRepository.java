package com.cbms.repository;

import com.cbms.entity.BankFinancingApplicationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankFinancingApplicationHistoryRepository
        extends JpaRepository<BankFinancingApplicationHistory, Long> {

    List<BankFinancingApplicationHistory>
    findByApplIdOrderByActionDtAscHistIdAsc(Long applId);
}