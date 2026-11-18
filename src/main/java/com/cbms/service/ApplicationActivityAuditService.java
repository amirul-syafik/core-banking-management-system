package com.cbms.service;

import com.cbms.entity.ApplicationActivityAudit;
import com.cbms.repository.ApplicationActivityAuditRepository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class ApplicationActivityAuditService {
    private final ApplicationActivityAuditRepository repository;

    public ApplicationActivityAuditService(ApplicationActivityAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actor, String method, String path, int status) {
        ApplicationActivityAudit audit = new ApplicationActivityAudit();
        audit.setActor(actor.length() > 100 ? actor.substring(0, 100) : actor);
        audit.setHttpMethod(method);
        audit.setRequestPath(path.length() > 500 ? path.substring(0, 500) : path);
        audit.setHttpStatus(status);
        audit.setOutcome(status < 400 ? "SUCCESS" : "FAILED");
        audit.setCreatedAt(OffsetDateTime.now());
        repository.save(audit);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationActivityAudit> search(String actor, String method, Integer status,
                                                 OffsetDateTime from, OffsetDateTime to, int page, int size) {
        return search(actor, method, status, from, to, page, size, null, null);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationActivityAudit> search(String actor, String method, Integer status,
                                                 OffsetDateTime from, OffsetDateTime to, int page, int size,
                                                 String sortBy, String sortDirection) {
        RequestFilterValidation.pagination(page, size);
        if (from != null && to != null && from.isAfter(to))
            throw new IllegalArgumentException("from must not exceed to");
        Specification<ApplicationActivityAudit> spec = (root, query, cb) -> cb.conjunction();
        if (actor != null && !actor.isBlank()) spec = spec.and((root, q, cb) -> cb.equal(root.get("actor"), actor));
        if (method != null && !method.isBlank())
            spec = spec.and((root, q, cb) -> cb.equal(root.get("httpMethod"), RequestFilterValidation.optionalUpper(method)));
        if (status != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("httpStatus"), status));
        if (from != null) spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        if (to != null) spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "auditId");
        if (sortBy != null || sortDirection != null) {
            if (sortBy == null || sortDirection == null)
                throw new IllegalArgumentException("sortBy and sortDirection must be provided together");
            String property = switch (sortBy.trim()) {
                case "createdAt" -> "createdAt";
                case "actor" -> "actor";
                case "httpMethod" -> "httpMethod";
                case "requestPath" -> "requestPath";
                case "httpStatus" -> "httpStatus";
                case "outcome" -> "outcome";
                default -> throw new IllegalArgumentException("Unsupported activity audit sort field");
            };
            Sort.Direction direction = switch (sortDirection.trim().toUpperCase(java.util.Locale.ROOT)) {
                case "ASC" -> Sort.Direction.ASC;
                case "DESC" -> Sort.Direction.DESC;
                default -> throw new IllegalArgumentException("sortDirection must be ASC or DESC");
            };
            sort = Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "auditId"));
        }
        return repository.findAll(spec, PageRequest.of(page, size, sort));
    }

    @Transactional(readOnly = true)
    public ApplicationActivityAudit get(Long id) {
        return repository.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Audit record not found"));
    }
}
