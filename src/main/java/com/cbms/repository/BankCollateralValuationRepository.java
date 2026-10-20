package com.cbms.repository;

import com.cbms.entity.BankCollateralValuation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankCollateralValuationRepository extends JpaRepository<BankCollateralValuation, Long> {
    boolean existsByCollateral_CollateralId(Long collateralId);

    boolean existsByCollateral_CollateralIdAndValuationTyp(Long collateralId, String valuationTyp);

    Optional<BankCollateralValuation> findFirstByCollateral_CollateralIdOrderByValuationDtDescValuationIdDesc(Long collateralId);

    Page<BankCollateralValuation> findByCollateral_CollateralId(Long collateralId, Pageable pageable);
}
