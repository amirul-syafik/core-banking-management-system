package com.cbms.service;

import com.cbms.dto.BankWorkflowAuditResponse;
import com.cbms.entity.BankWorkflowHistory;
import com.cbms.repository.BankWorkflowAuditRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;

@Service
public class BankWorkflowAuditService {

    private static final Set<String> ACTIONS = Set.of(
            "SUBMIT",
            "ASSIGN",
            "CLAIM",
            "APPROVE",
            "REJECT",
            "CANCEL",
            "COMPLETE"
    );

    private final BankWorkflowAuditRepository repository;

    public BankWorkflowAuditService(
            BankWorkflowAuditRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<BankWorkflowAuditResponse> search(
            Long instanceId,
            Long workflowId,
            String actionCd,
            String actionBy,
            OffsetDateTime fromDt,
            OffsetDateTime toDt,
            int page,
            int size
    ) {

        // Validate pagination
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be >= 0 and size must be between 1 and 100"
            );
        }

        // Validate identifiers
        if ((instanceId != null && instanceId <= 0)
                || (workflowId != null && workflowId <= 0)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "IDs must be positive"
            );
        }

        // Validate action
        String action = blankToNull(actionCd);

        if (action != null) {
            action = action.toUpperCase(Locale.ROOT);

            if (!ACTIONS.contains(action)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid actionCd"
                );
            }
        }

        // Validate actor
        String actor = blankToNull(actionBy);

        if (actor != null && actor.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "actionBy exceeds 100 characters"
            );
        }

        // Validate date range
        if (fromDt != null && toDt != null
                && !fromDt.isBefore(toDt)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "fromDt must precede toDt"
            );
        }

        // Build dynamic search specification
        Specification<BankWorkflowHistory> specification =
                (root, query, cb) -> cb.conjunction();

        if (instanceId != null) {
            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(
                                    root.get("instance").get("instanceId"),
                                    instanceId
                            )
            );
        }

        if (workflowId != null) {
            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(
                                    root.get("instance")
                                            .get("workflow")
                                            .get("workflowId"),
                                    workflowId
                            )
            );
        }

        if (action != null) {
            final String finalAction = action;

            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(
                                    root.get("actionCd"),
                                    finalAction
                            )
            );
        }

        if (actor != null) {
            final String finalActor = actor;

            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(
                                    root.get("actionBy"),
                                    finalActor
                            )
            );
        }

        if (fromDt != null) {
            specification = specification.and(
                    (root, query, cb) ->
                            cb.greaterThanOrEqualTo(
                                    root.<OffsetDateTime>get("actionDt"),
                                    fromDt
                            )
            );
        }

        if (toDt != null) {
            specification = specification.and(
                    (root, query, cb) ->
                            cb.lessThan(
                                    root.<OffsetDateTime>get("actionDt"),
                                    toDt
                            )
            );
        }

        // Preserve descending audit chronology
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("actionDt"),
                        Sort.Order.desc("histId")
                )
        );

        return repository.findAll(specification, pageable)
                .map(this::toResponse);
    }

    private BankWorkflowAuditResponse toResponse(
            BankWorkflowHistory h
    ) {

        var instance = h.getInstance();
        var workflow = instance.getWorkflow();

        return new BankWorkflowAuditResponse(
                h.getHistId(),
                instance.getInstanceId(),
                instance.getInstanceRef(),
                workflow.getWorkflowId(),
                workflow.getWorkflowCd(),
                instance.getEntityId(),
                instance.getEntityRef(),
                h.getTaskId(),
                h.getActionCd(),
                h.getFromSts(),
                h.getToSts(),
                h.getActionRemarks(),
                h.getActionBy(),
                h.getActionDt()
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
