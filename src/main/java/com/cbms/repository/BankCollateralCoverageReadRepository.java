package com.cbms.repository;

import com.cbms.entity.BankCollateral;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankCollateralCoverageReadRepository extends JpaRepository<BankCollateral, Long> {
}
