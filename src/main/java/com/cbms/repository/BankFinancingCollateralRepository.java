package com.cbms.repository;

import com.cbms.entity.BankFinancingCollateral;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface BankFinancingCollateralRepository extends JpaRepository<BankFinancingCollateral, Long> {
    @Query("select l from BankFinancingCollateral l join fetch l.facility f where l.collateral.owner.custId = :custId and l.linkSts = 'ACTIVE' and l.facility is not null")
    List<BankFinancingCollateral> findActiveFacilityAllocationsForOwner(@Param("custId") Long custId);

    boolean existsByCollateral_CollateralIdAndLinkSts(Long collateralId, String linkSts);

    boolean existsByApplication_ApplIdAndCollateral_CollateralIdAndLinkStsIn(Long applId, Long collateralId, List<String> statuses);

    List<BankFinancingCollateral> findByApplication_ApplIdOrderByLinkIdDesc(Long applId);

    List<BankFinancingCollateral> findByFacility_FacIdOrderByLinkIdDesc(Long facId);

    List<BankFinancingCollateral> findByCollateral_CollateralIdOrderByLinkIdDesc(Long collateralId);

    @Query("select coalesce(sum(l.allocatedAmount),0) from BankFinancingCollateral l where l.collateral.collateralId=:id and l.linkSts='ACTIVE' and (:excludeId is null or l.linkId<>:excludeId)")
    BigDecimal activeAllocated(@Param("id") Long collateralId, @Param("excludeId") Long excludeId);
}
