package com.cbms.repository;

import com.cbms.entity.BankAccountStatusRequest;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.*;

public interface BankAccountStatusRequestRepository extends JpaRepository<BankAccountStatusRequest, Long> {
    List<BankAccountStatusRequest> findByAcctIdOrderByRequestedAtDesc(Long acctId);

    boolean existsByAcctIdAndRequestStatus(Long acctId, String requestStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BankAccountStatusRequest r where r.requestId=:id")
    Optional<BankAccountStatusRequest> lockById(@Param("id") Long id);
}
