package com.cbms.repository;

import com.cbms.entity.CustomerEmployment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerEmploymentRepository
        extends JpaRepository<CustomerEmployment, Long> {

    List<CustomerEmployment> findByCustId(Long custId);
}
