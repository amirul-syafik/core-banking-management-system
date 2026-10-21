package com.cbms.repository;

import com.cbms.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface CustomerStatusLockedRepository extends JpaRepository<Customer, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.custId = :id")
    Optional<Customer> findLocked(@Param("id") Long id);
}
