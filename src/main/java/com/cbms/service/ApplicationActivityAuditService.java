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
        return repository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "auditId")));
    }

    @Transactional(readOnly = true)
    public ApplicationActivityAudit get(Long id) {
        return repository.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Audit record not found"));
    }
}
