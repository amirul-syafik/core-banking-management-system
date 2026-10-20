package com.cbms.repository;

import com.cbms.entity.BankFinancingApplication;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface BankFinancingApplicationCollateralRepository extends JpaRepository<BankFinancingApplication, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from BankFinancingApplication a where a.applId=:id")
    Optional<BankFinancingApplication> lock(@Param("id") Long id);
}
