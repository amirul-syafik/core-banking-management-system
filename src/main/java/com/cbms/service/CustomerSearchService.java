package com.cbms.service;

import com.cbms.entity.Customer;
import com.cbms.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class CustomerSearchService {

    private final CustomerRepository customerRepository;

    public CustomerSearchService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Page<Customer> search(
            String name,
            String type,
            String status,
            int page,
            int size) {

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative");
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100");
        }

        Specification<Customer> specification =
                (root, query, cb) -> cb.conjunction();

        if (name != null && !name.isBlank()) {
            String keyword = "%"
                    + name.trim().toLowerCase(Locale.ROOT)
                    + "%";

            specification = specification.and(
                    (root, query, cb) ->
                            cb.like(
                                    cb.lower(root.get("custNm")),
                                    keyword));
        }

        if (type != null && !type.isBlank()) {
            String normalizedType =
                    type.trim().toUpperCase(Locale.ROOT);

            if (!normalizedType.equals("INDIVIDUAL")
                    && !normalizedType.equals("CORPORATE")) {
                throw new IllegalArgumentException(
                        "Type must be INDIVIDUAL or CORPORATE");
            }

            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(
                                    cb.upper(root.get("custTyp")),
                                    normalizedType));
        }

        if (status != null && !status.isBlank()) {
            String normalizedStatus =
                    status.trim().toUpperCase(Locale.ROOT);

            if (!normalizedStatus.equals("PENDING")
                    && !normalizedStatus.equals("ACTIVE")
                    && !normalizedStatus.equals("INACTIVE")
                    && !normalizedStatus.equals("BLOCKED")) {
                throw new IllegalArgumentException(
                        "Status must be PENDING, ACTIVE, INACTIVE, or BLOCKED");
            }

            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(
                                    cb.upper(root.get("sts")),
                                    normalizedStatus));
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.ASC, "custId"));

        return customerRepository.findAll(specification, pageable);
    }
}
