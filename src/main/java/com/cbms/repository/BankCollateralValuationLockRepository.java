package com.cbms.repository;

import com.cbms.entity.BankCollateral;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BankCollateralValuationLockRepository extends JpaRepository<BankCollateral, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BankCollateral c where c.collateralId=:id")
    Optional<BankCollateral> lockCollateral(@Param("id") Long id);
}
