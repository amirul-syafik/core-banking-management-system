package com.cbms.repository;

import com.cbms.entity.BankAccountApplicationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankAccountApplicationHistoryRepository
        extends JpaRepository<BankAccountApplicationHistory, Long> {

    List<BankAccountApplicationHistory>
    findByApplIdOrderByActionDtAscHistIdAsc(Long applId);
}