package com.cbms.repository;

import com.cbms.entity.BankVehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface BankVehicleRepository extends JpaRepository<BankVehicle, Long>, JpaSpecificationExecutor<BankVehicle> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from BankVehicle v where v.vehicleId = :vehicleId")
    Optional<BankVehicle> lockById(@Param("vehicleId") Long vehicleId);

    boolean existsByChassisNoIgnoreCase(String chassisNo);

    boolean existsByChassisNoIgnoreCaseAndVehicleIdNot(String chassisNo, Long vehicleId);

    @Query("select (count(c) > 0) from BankCollateral c where c.vehicle.vehicleId = :vehicleId and c.collateralSts = 'REGISTERED'")
    boolean isVehicleSecured(@Param("vehicleId") Long vehicleId);
}
