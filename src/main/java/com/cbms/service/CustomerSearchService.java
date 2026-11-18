package com.cbms.service;

import com.cbms.entity.Customer;
import com.cbms.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class CustomerSearchService {

    private final CustomerRepository customerRepository;

    public CustomerSearchService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public Page<Customer> search(
            String name,
            String type,
            String status,
            int page,
            int size) {
        return search(name, type, status, page, size, null, null);
    }

    @Transactional(readOnly = true)
    public Page<Customer> search(
            String name,
            String type,
            String status,
            int page,
            int size,
            String sortBy,
            String sortDirection) {

        RequestFilterValidation.pagination(page, size);

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

        Sort sort = Sort.by(Sort.Direction.ASC, "custId");
        if (sortBy != null || sortDirection != null) {
            if (sortBy == null || sortDirection == null)
                throw new IllegalArgumentException("sortBy and sortDirection must be provided together");
            String property = switch (sortBy.trim()) {
                case "cifNo" -> "cifNo";
                case "custNm" -> "custNm";
                case "custTyp" -> "custTyp";
                case "sts" -> "sts";
                default -> throw new IllegalArgumentException("Unsupported customer sort field");
            };
            Sort.Direction direction = switch (sortDirection.trim().toUpperCase(Locale.ROOT)) {
                case "ASC" -> Sort.Direction.ASC;
                case "DESC" -> Sort.Direction.DESC;
                default -> throw new IllegalArgumentException("sortDirection must be ASC or DESC");
            };
            sort = Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "custId"));
        }

        Pageable pageable = PageRequest.of(page, size, sort);

        return customerRepository.findAll(specification, pageable);
    }
}
