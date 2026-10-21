package com.cbms.repository;

import com.cbms.entity.CustomerStatusRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerStatusRequestRepository extends JpaRepository<CustomerStatusRequest, Long> {
    List<CustomerStatusRequest> findByCustIdOrderByRequestedAtDesc(Long custId);

    boolean existsByCustIdAndRequestStatus(Long custId, String requestStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CustomerStatusRequest r where r.requestId = :id")
    Optional<CustomerStatusRequest> findLocked(@Param("id") Long id);
}
