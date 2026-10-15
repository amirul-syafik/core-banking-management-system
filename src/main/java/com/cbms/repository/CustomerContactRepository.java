package com.cbms.repository;

import com.cbms.entity.CustomerContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerContactRepository
        extends JpaRepository<CustomerContact, Long> {

    List<CustomerContact> findByCustId(Long custId);
}