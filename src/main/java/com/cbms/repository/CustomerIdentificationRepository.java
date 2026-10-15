package com.cbms.repository;

import com.cbms.entity.CustomerIdentification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerIdentificationRepository
        extends JpaRepository<CustomerIdentification, Long> {

    List<CustomerIdentification> findByCustIdOrderByIdnIdDesc(Long custId);

    Optional<CustomerIdentification> findByIdnIdAndCustId(
            Long idnId,
            Long custId
    );
}