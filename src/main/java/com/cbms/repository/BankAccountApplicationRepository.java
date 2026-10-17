package com.cbms.repository;

import com.cbms.entity.BankAccountApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;
import java.util.Optional;

public interface BankAccountApplicationRepository
        extends JpaRepository<BankAccountApplication, Long> {

    Optional<BankAccountApplication> findByApplRef(String applRef);

    List<BankAccountApplication>
    findByCustIdOrderByCrtDtDesc(Long custId);

    List<BankAccountApplication>
    findByApplStsOrderByCrtDtDesc(String applSts);

    List<BankAccountApplication>
    findAllByOrderByCrtDtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM BankAccountApplication a WHERE a.applId = :applId")
    Optional<BankAccountApplication> findByIdForUpdate(
            @Param("applId") Long applId
    );
}