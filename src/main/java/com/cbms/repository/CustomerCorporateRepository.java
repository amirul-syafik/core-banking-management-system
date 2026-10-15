package com.cbms.repository;

import com.cbms.entity.CustomerCorporate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerCorporateRepository
        extends JpaRepository<CustomerCorporate, Long> {

    boolean existsByRegCtyAndRegNo(String regCty, String regNo);
}
