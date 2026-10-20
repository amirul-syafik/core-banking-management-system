package com.cbms.service;

import com.cbms.dto.*;
import com.cbms.repository.BankWorkflowMonitoringRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;

@Service
public class BankWorkflowMonitoringService {
    private static final Set<String> ROLES = Set.of("MAKER", "ADMIN");
    private final BankWorkflowMonitoringRepository repository;

    public BankWorkflowMonitoringService(BankWorkflowMonitoringRepository repository) {
        this.repository = repository;
    }

    private void validateHours(int hours) {
        if (hours < 1 || hours > 8760) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "overdueHours must be between 1 and 8760");
        }
    }

    private void validatePaging(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size must be between 1 and 100");
        }
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) return null;
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assignedRole must be MAKER or ADMIN");
        }
        return normalized;
    }

    @Transactional(readOnly = true)
    public BankWorkflowMonitoringSummaryResponse summary(int overdueHours) {
        validateHours(overdueHours);
        return repository.summary(overdueHours, OffsetDateTime.now(ZoneOffset.UTC).minusHours(overdueHours));
    }

    @Transactional(readOnly = true)
    public BankWorkflowMonitoringTaskPageResponse pending(String assignedRole, int overdueHours, int page, int size) {
        validateHours(overdueHours);
        validatePaging(page, size);
        return repository.tasks(normalizeRole(assignedRole), false, overdueHours,
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(overdueHours), page, size);
    }

    @Transactional(readOnly = true)
    public BankWorkflowMonitoringTaskPageResponse overdue(String assignedRole, int overdueHours, int page, int size) {
        validateHours(overdueHours);
        validatePaging(page, size);
        return repository.tasks(normalizeRole(assignedRole), true, overdueHours,
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(overdueHours), page, size);
    }

    @Transactional(readOnly = true)
    public BankWorkflowMonitoringStatisticsResponse statistics() {
        return repository.statistics();
    }
}
