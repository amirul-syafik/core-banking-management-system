package com.cbms.repository;

import com.cbms.entity.CustomerKyc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerKycRepository
        extends JpaRepository<CustomerKyc, Long> {

    List<CustomerKyc> findByCustId(Long custId);
}