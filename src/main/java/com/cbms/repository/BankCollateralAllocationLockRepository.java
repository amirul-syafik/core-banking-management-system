package com.cbms.repository;

import com.cbms.entity.BankCollateral;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface BankCollateralAllocationLockRepository extends JpaRepository<BankCollateral, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BankCollateral c where c.collateralId=:id")
    Optional<BankCollateral> lock(@Param("id") Long id);
}
