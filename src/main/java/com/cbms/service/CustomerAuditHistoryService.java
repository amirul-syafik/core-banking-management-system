package com.cbms.service;

import com.cbms.entity.CustomerAuditHistory;
import com.cbms.repository.CustomerAuditHistoryRepository;
import com.cbms.repository.CustomerRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class CustomerAuditHistoryService {

    private final CustomerAuditHistoryRepository auditRepository;
    private final CustomerRepository customerRepository;

    public CustomerAuditHistoryService(
            CustomerAuditHistoryRepository auditRepository,
            CustomerRepository customerRepository) {

        this.auditRepository = auditRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public Page<CustomerAuditHistory> findByCustomer(
            Long custId,
            String actionTyp,
            int page,
            int size) {

        if (!customerRepository.existsById(custId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Customer not found"
            );
        }

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be 0 or greater"
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 100"
            );
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("changedDt"),
                        Sort.Order.desc("auditId")
                )
        );

        if (actionTyp == null || actionTyp.isBlank()) {
            return auditRepository.findByCustId(
                    custId,
                    pageable
            );
        }

        String normalizedAction = actionTyp
                .trim()
                .toUpperCase(Locale.ROOT);

        return auditRepository.findByCustIdAndActionTyp(
                custId,
                normalizedAction,
                pageable
        );
    }
}
