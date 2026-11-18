package com.cbms.repository;

import com.cbms.entity.BankCollateral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface BankCollateralRepository extends JpaRepository<BankCollateral, Long>, JpaSpecificationExecutor<BankCollateral> {
    List<BankCollateral> findByOwner_CustId(Long custId);

    boolean existsByVehicle_VehicleIdAndCollateralSts(Long vehicleId, String collateralSts);

    boolean existsByVehicle_VehicleIdAndCollateralStsAndCollateralIdNot(Long vehicleId, String collateralSts, Long collateralId);

    boolean existsByVehicle_VehicleIdAndCollateralStsIn(Long vehicleId, List<String> collateralStatuses);
}
