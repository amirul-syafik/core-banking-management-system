package com.cbms.repository;

import com.cbms.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerAddressRepository
        extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByCustIdOrderByAdrIdAsc(Long custId);
}