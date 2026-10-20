package com.cbms.repository;

import com.cbms.entity.BankFinancingFacility;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankFinancingFacilityCollateralRepository extends JpaRepository<BankFinancingFacility, Long> {
}
