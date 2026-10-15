package com.cbms.repository;

import com.cbms.entity.CustomerRelationship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRelationshipRepository
        extends JpaRepository<CustomerRelationship, Long> {

    List<CustomerRelationship> findByCustId(Long custId);

    List<CustomerRelationship> findByRelCustId(Long relCustId);
}