package com.cbms.repository;

import com.cbms.entity.CustomerDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerDocumentRepository
        extends JpaRepository<CustomerDocument, Long> {

    List<CustomerDocument> findByCustIdOrderByUploadedDtDesc(Long custId);

    Optional<CustomerDocument> findByDocIdAndCustId(
            Long docId,
            Long custId
    );
}