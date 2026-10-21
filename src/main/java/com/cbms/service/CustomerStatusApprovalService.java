package com.cbms.service;

import com.cbms.dto.*;
import com.cbms.entity.*;
import com.cbms.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class CustomerStatusApprovalService {
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PENDING", Set.of("ACTIVE", "INACTIVE", "BLOCKED"),
            "ACTIVE", Set.of("INACTIVE", "BLOCKED"),
            "INACTIVE", Set.of("ACTIVE", "BLOCKED"),
            "BLOCKED", Set.of("INACTIVE")
    );
    private final CustomerStatusLockedRepository customers;
    private final CustomerStatusRequestRepository requests;
    private final CustomerAuditHistoryRepository audits;

    public CustomerStatusApprovalService(CustomerStatusLockedRepository customers,
                                         CustomerStatusRequestRepository requests, CustomerAuditHistoryRepository audits) {
        this.customers = customers;
        this.requests = requests;
        this.audits = audits;
    }

    private Authentication auth() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || "anonymousUser".equals(a.getName()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        return a;
    }

    private boolean admin(Authentication a) {
        return a.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_ADMIN"));
    }

    private Customer lockedCustomer(Long id) {
        return customers.findLocked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    @Transactional
    public CustomerStatusRequestView propose(Long id, CustomerStatusProposal proposal) {
        Authentication a = auth();
        Customer c = lockedCustomer(id);
        String current = c.getSts().toUpperCase(Locale.ROOT);
        String target = proposal.status().toUpperCase(Locale.ROOT);
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status transition");
        if (requests.existsByCustIdAndRequestStatus(id, "PENDING"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A pending status request already exists");
        CustomerStatusRequest r = new CustomerStatusRequest();
        r.setCustId(id);
        r.setFromStatus(current);
        r.setToStatus(target);
        r.setReason(proposal.reason().trim());
        r.setRequestStatus("PENDING");
        r.setRequestedBy(a.getName());
        r.setRequestedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return CustomerStatusRequestView.from(requests.save(r), false);
    }

    @Transactional(readOnly = true)
    public List<CustomerStatusRequestView> list(Long id) {
        Authentication a = auth();
        if (!customers.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        return requests.findByCustIdOrderByRequestedAtDesc(id).stream()
                .map(r -> CustomerStatusRequestView.from(r, admin(a) && !a.getName().equals(r.getRequestedBy())
                        && "PENDING".equals(r.getRequestStatus()))).toList();
    }

    @Transactional
    public CustomerStatusRequestView decide(Long id, Long requestId, CustomerStatusDecision decision) {
        Authentication a = auth();
        if (!admin(a)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN required");
        Customer c = lockedCustomer(id);
        CustomerStatusRequest r = requests.findLocked(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (!r.getCustId().equals(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
        if (!"PENDING".equals(r.getRequestStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request already reviewed");
        if (a.getName().equals(r.getRequestedBy()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requester cannot approve their own request");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (Boolean.TRUE.equals(decision.approve())) {
            if (!c.getSts().equalsIgnoreCase(r.getFromStatus()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer status changed since request");
            if (!TRANSITIONS.getOrDefault(c.getSts().toUpperCase(Locale.ROOT), Set.of()).contains(r.getToStatus()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Transition is no longer valid");
            c.setSts(r.getToStatus());
            c.setUpdDt(now);
            c.setUpdBy(a.getName());
            customers.save(c);
            CustomerAuditHistory audit = new CustomerAuditHistory();
            audit.setCustId(id);
            audit.setActionTyp("STATUS_CHANGE");
            audit.setOldVal(r.getFromStatus());
            audit.setNewVal(r.getToStatus());
            audit.setChangedBy(a.getName());
            audit.setChangedDt(now);
            audits.save(audit);
            r.setRequestStatus("APPROVED");
        } else r.setRequestStatus("REJECTED");
        r.setReviewedBy(a.getName());
        r.setReviewedAt(now);
        return CustomerStatusRequestView.from(requests.save(r), false);
    }
}
