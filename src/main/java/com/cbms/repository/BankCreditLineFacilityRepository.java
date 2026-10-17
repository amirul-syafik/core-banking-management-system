package com.cbms.repository;

import com.cbms.entity.BankCreditLineFacility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankCreditLineFacilityRepository
        extends JpaRepository<BankCreditLineFacility, Long> {

    Optional<BankCreditLineFacility> findByClFacRef(String clFacRef);

    List<BankCreditLineFacility> findByCustIdOrderByClFacIdDesc(Long custId);
}