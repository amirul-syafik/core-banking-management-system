package com.cbms.service;

import com.cbms.dto.CustomerStatusUpdateRequest;
import com.cbms.entity.Customer;
import com.cbms.entity.CustomerAuditHistory;
import com.cbms.repository.CustomerAuditHistoryRepository;
import com.cbms.repository.CustomerRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CustomerStatusService {

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS =
            Map.of(
                    "PENDING", Set.of("ACTIVE", "INACTIVE", "BLOCKED"),
                    "ACTIVE", Set.of("INACTIVE", "BLOCKED"),
                    "INACTIVE", Set.of("ACTIVE", "BLOCKED"),
                    "BLOCKED", Set.of("INACTIVE")
            );

    private final CustomerRepository customerRepository;
    private final CustomerAuditHistoryRepository auditRepository;

    public CustomerStatusService(
            CustomerRepository customerRepository,
            CustomerAuditHistoryRepository auditRepository) {

        this.customerRepository = customerRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public Customer updateStatus(
            Long custId,
            CustomerStatusUpdateRequest request) {

        Customer customer = customerRepository.findById(custId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found"
                ));

        String currentStatus = customer.getSts()
                .trim()
                .toUpperCase(Locale.ROOT);

        String newStatus = request.getSts()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (currentStatus.equals(newStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Customer already has status " + currentStatus
            );
        }

        Set<String> allowedStatuses =
                ALLOWED_TRANSITIONS.get(currentStatus);

        if (allowedStatuses == null
                || !allowedStatuses.contains(newStatus)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status transition from " + currentStatus
                            + " to " + newStatus + " is not allowed"
            );
        }

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user is required"
            );
        }

        String changedBy = authentication.getName();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        customer.setSts(newStatus);
        customer.setUpdDt(now);
        customer.setUpdBy(changedBy);

        Customer savedCustomer = customerRepository.save(customer);

        CustomerAuditHistory audit = new CustomerAuditHistory();
        audit.setCustId(custId);
        audit.setActionTyp("STATUS_CHANGE");
        audit.setOldVal(currentStatus);
        audit.setNewVal(newStatus);
        audit.setChangedBy(changedBy);
        audit.setChangedDt(now);

        auditRepository.save(audit);

        return savedCustomer;
    }
}
