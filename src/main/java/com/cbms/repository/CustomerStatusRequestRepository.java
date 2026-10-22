package com.cbms.repository;

import com.cbms.entity.CustomerStatusRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.cbms.dto.CustomerStatusHistoryRow;

import java.util.Optional;

public interface CustomerStatusRequestRepository extends JpaRepository<CustomerStatusRequest, Long> {
    List<CustomerStatusRequest> findByCustIdOrderByRequestedAtDesc(Long custId);

    @Query("select new com.cbms.dto.CustomerStatusHistoryRow(c.cifNo, c.custNm, r.fromStatus, r.toStatus, r.requestStatus, r.reason, r.requestedAt, r.reviewedAt) " +
            "from CustomerStatusRequest r, Customer c where c.custId = r.custId order by r.requestedAt desc, r.requestId desc")
    Page<CustomerStatusHistoryRow> findHistory(Pageable pageable);


    boolean existsByCustIdAndRequestStatus(Long custId, String requestStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CustomerStatusRequest r where r.requestId = :id")
    Optional<CustomerStatusRequest> findLocked(@Param("id") Long id);
}
