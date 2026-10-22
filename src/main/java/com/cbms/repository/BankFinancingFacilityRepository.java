package com.cbms.repository;

import com.cbms.entity.BankFinancingFacility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankFinancingFacilityRepository
        extends JpaRepository<BankFinancingFacility, Long> {

    List<BankFinancingFacility> findByCustIdOrderByFacIdDesc(Long custId);

    List<BankFinancingFacility> findByFacStsOrderByFacIdDesc(String facSts);

    List<BankFinancingFacility> findAllByOrderByFacIdDesc();

    Optional<BankFinancingFacility> findByApplId(Long applId);
}