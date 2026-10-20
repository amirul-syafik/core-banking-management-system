package com.cbms.repository;

import com.cbms.entity.BankVehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BankVehicleRepository extends JpaRepository<BankVehicle, Long>, JpaSpecificationExecutor<BankVehicle> {
    boolean existsByChassisNoIgnoreCase(String chassisNo);

    boolean existsByChassisNoIgnoreCaseAndVehicleIdNot(String chassisNo, Long vehicleId);
}
