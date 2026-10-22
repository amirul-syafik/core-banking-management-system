package com.cbms.repository;

import com.cbms.entity.BankFinancingApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BankFinancingApplicationRepository
        extends JpaRepository<BankFinancingApplication, Long> {

    Optional<BankFinancingApplication> findByApplRef(String applRef);

    List<BankFinancingApplication> findByCustIdOrderByApplIdDesc(Long custId);

    List<BankFinancingApplication> findByApplStsOrderByApplIdDesc(String applSts);

    List<BankFinancingApplication> findAllByOrderByApplIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a
            FROM BankFinancingApplication a
            WHERE a.applId = :applId
            """)
    Optional<BankFinancingApplication> findByIdForUpdate(
            @Param("applId") Long applId
    );
}