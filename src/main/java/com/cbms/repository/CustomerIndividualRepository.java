package com.cbms.repository;

import com.cbms.entity.CustomerIndividual;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerIndividualRepository
        extends JpaRepository<CustomerIndividual, Long> {
}
