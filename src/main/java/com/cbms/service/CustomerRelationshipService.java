package com.cbms.service;

import com.cbms.dto.CustomerRelationshipRequest;
import com.cbms.entity.CustomerRelationship;
import com.cbms.repository.CustomerRelationshipRepository;
import com.cbms.repository.CustomerRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
public class CustomerRelationshipService {

    private final CustomerRelationshipRepository relationshipRepository;
    private final CustomerRepository customerRepository;

    public CustomerRelationshipService(
            CustomerRelationshipRepository relationshipRepository,
            CustomerRepository customerRepository) {
        this.relationshipRepository = relationshipRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerRelationship createRelationship(
            Long custId,
            CustomerRelationshipRequest request) {

        if (!customerRepository.existsById(custId)
                || !customerRepository.existsById(request.getRelCustId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer or related customer not found"
            );
        }

        if (custId.equals(request.getRelCustId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A customer cannot be related to itself"
            );
        }

        LocalDate effectiveDate = request.getEffDt() == null
                ? LocalDate.now(ZoneOffset.UTC)
                : request.getEffDt();

        if (request.getEndDt() != null
                && request.getEndDt().isBefore(effectiveDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "End date cannot be before the effective date"
            );
        }

        String relationshipType = request.getRelTyp() == null
                ? null
                : request.getRelTyp().trim().toUpperCase(Locale.ROOT);

        String status = request.getSts() == null
                ? "ACTIVE"
                : request.getSts().trim().toUpperCase(Locale.ROOT);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        CustomerRelationship relationship = new CustomerRelationship();
        relationship.setCustId(custId);
        relationship.setRelCustId(request.getRelCustId());
        relationship.setRelTyp(relationshipType);
        relationship.setSts(status);
        relationship.setEffDt(effectiveDate);
        relationship.setEndDt(request.getEndDt());
        relationship.setCrtDt(now);
        relationship.setCrtBy("SYSTEM");
        relationship.setUpdDt(now);
        relationship.setUpdBy(null);

        return relationshipRepository.save(relationship);
    }

    @Transactional(readOnly = true)
    public List<CustomerRelationship> getRelationships(Long custId) {

        if (!customerRepository.existsById(custId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found"
            );
        }

        return relationshipRepository.findByCustId(custId);
    }
}