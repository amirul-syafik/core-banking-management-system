package com.cbms.repository;

import com.cbms.entity.BankCollateral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BankCollateralRepository extends JpaRepository<BankCollateral, Long>, JpaSpecificationExecutor<BankCollateral> {
    boolean existsByVehicle_VehicleIdAndCollateralSts(Long vehicleId, String collateralSts);

    boolean existsByVehicle_VehicleIdAndCollateralStsAndCollateralIdNot(Long vehicleId, String collateralSts, Long collateralId);
}
