package com.cbms.service;

import com.cbms.dto.*;
import com.cbms.entity.*;
import com.cbms.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class BankAccountStatusApprovalService {
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PENDING", Set.of("ACTIVE"), "ACTIVE", Set.of("DORMANT", "FROZEN"),
            "DORMANT", Set.of("ACTIVE", "FROZEN"), "FROZEN", Set.of("ACTIVE"), "CLOSED", Set.of());
    private final BankAccountRepository accounts;
    private final BankAccountStatusRequestRepository requests;
    private final BankAccountAuditHistoryRepository audits;

    public BankAccountStatusApprovalService(BankAccountRepository accounts, BankAccountStatusRequestRepository requests, BankAccountAuditHistoryRepository audits) {
        this.accounts = accounts;
        this.requests = requests;
        this.audits = audits;
    }

    private BankAccount account(Long id) {
        return accounts.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private static String user(Authentication a) {
        if (a == null || !a.isAuthenticated() || "anonymousUser".equals(a.getName()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        return a.getName();
    }

    private static boolean admin(Authentication a) {
        return a.getAuthorities().stream().anyMatch(x -> "ROLE_ADMIN".equals(x.getAuthority()));
    }

    @Transactional
    public BankAccountStatusRequestView propose(Long acctId, BankAccountStatusProposal proposal, Authentication auth) {
        String username = user(auth);
        BankAccount a = account(acctId);
        String from = a.getAcctSts();
        String to = proposal.status();
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status transition");
        if (requests.existsByAcctIdAndRequestStatus(acctId, "PENDING"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An approval is already pending");
        BankAccountStatusRequest r = new BankAccountStatusRequest();
        r.setAcctId(acctId);
        r.setFromStatus(from);
        r.setToStatus(to);
        r.setReason(proposal.reason().trim());
        r.setRequestStatus("PENDING");
        r.setRequestedBy(username);
        r.setRequestedAt(OffsetDateTime.now());
        return BankAccountStatusRequestView.of(requests.save(r), false);
    }

    @Transactional(readOnly = true)
    public List<BankAccountStatusRequestView> list(Long acctId, Authentication auth) {
        String username = user(auth);
        if (!accounts.existsById(acctId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        return requests.findByAcctIdOrderByRequestedAtDesc(acctId).stream().map(r -> BankAccountStatusRequestView.of(r, admin(auth) && "PENDING".equals(r.getRequestStatus()) && !username.equals(r.getRequestedBy()))).toList();
    }

    @Transactional
    public BankAccountStatusRequestView decide(Long acctId, Long requestId, BankAccountStatusDecision decision, Authentication auth) {
        String username = user(auth);
        if (!admin(auth)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN required");
        BankAccount a = account(acctId);
        BankAccountStatusRequest r = requests.lockById(requestId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (!acctId.equals(r.getAcctId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
        if (!"PENDING".equals(r.getRequestStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request already reviewed");
        if (username.equals(r.getRequestedBy()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Self-approval is prohibited");
        OffsetDateTime now = OffsetDateTime.now();
        if (Boolean.TRUE.equals(decision.approve())) {
            if (!r.getFromStatus().equals(a.getAcctSts()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Account status changed since request");
            if (!TRANSITIONS.getOrDefault(a.getAcctSts(), Set.of()).contains(r.getToStatus()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Transition no longer allowed");
            a.setAcctSts(r.getToStatus());
            a.setUpdBy(username);
            a.setUpdDt(now);
            accounts.save(a);
            BankAccountAuditHistory audit = new BankAccountAuditHistory();
            audit.setAcctId(acctId);
            audit.setActionTyp("STATUS_CHANGE");
            audit.setOldVal(r.getFromStatus());
            audit.setNewVal(r.getToStatus());
            audit.setChangedBy(username);
            audit.setChangedDt(now);
            audits.save(audit);
            r.setRequestStatus("APPROVED");
        } else r.setRequestStatus("REJECTED");
        r.setReviewedBy(username);
        r.setReviewedAt(now);
        return BankAccountStatusRequestView.of(requests.save(r), false);
    }
}
