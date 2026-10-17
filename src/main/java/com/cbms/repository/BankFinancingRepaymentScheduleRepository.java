package com.cbms.repository;

import com.cbms.entity.BankFinancingRepaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankFinancingRepaymentScheduleRepository
        extends JpaRepository<BankFinancingRepaymentSchedule, Long> {

    List<BankFinancingRepaymentSchedule>
    findByFacIdOrderByInstNoAsc(Long facId);
}