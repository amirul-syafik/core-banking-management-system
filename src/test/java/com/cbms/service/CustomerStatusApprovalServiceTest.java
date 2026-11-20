package com.cbms.service;

import com.cbms.dto.CustomerStatusDecision;
import com.cbms.dto.CustomerStatusProposal;
import com.cbms.dto.CustomerStatusRequestView;
import com.cbms.entity.Customer;
import com.cbms.entity.CustomerAuditHistory;
import com.cbms.entity.CustomerStatusRequest;
import com.cbms.repository.CustomerAuditHistoryRepository;
import com.cbms.repository.CustomerStatusLockedRepository;
import com.cbms.repository.CustomerStatusRequestRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerStatusApprovalServiceTest {
    @Mock private CustomerStatusLockedRepository customers;
    @Mock private CustomerStatusRequestRepository requests;
    @Mock private CustomerAuditHistoryRepository audits;
    @Mock private BankApprovalNotificationService notifications;

    private CustomerStatusApprovalService service;
    private Customer customer;

    @BeforeEach
    void setUp() {
        service = new CustomerStatusApprovalService(customers, requests, audits);
        service.setNotificationService(notifications);
        setMaker("customer-maker");
        customer = new Customer();
        customer.setCustId(7L);
        customer.setSts("ACTIVE");
        when(customers.findLocked(7L)).thenReturn(Optional.of(customer));
        lenient().when(requests.existsByCustIdAndRequestStatus(7L, "PENDING")).thenReturn(false);
        lenient().when(requests.saveAndFlush(any(CustomerStatusRequest.class))).thenAnswer(invocation -> {
            CustomerStatusRequest request = invocation.getArgument(0);
            if (request.getRequestId() == null) ReflectionTestUtils.setField(request, "requestId", 41L);
            return request;
        });
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void proposalCreatesStandardCustomerStatusRequestAndNotifiesReviewers() {
        CustomerStatusRequestView result = service.propose(7L,
                new CustomerStatusProposal("BLOCKED", "Risk review"));

        assertEquals("PENDING", result.requestStatus());
        assertEquals("ACTIVE", result.fromStatus());
        assertEquals("BLOCKED", result.toStatus());
        assertEquals("Risk review", result.reason());
        assertFalse(result.canReview());
        verify(requests).saveAndFlush(argThat(request -> "PENDING".equals(request.getRequestStatus())
                && "customer-maker".equals(request.getRequestedBy())
                && request.getReason().equals("Risk review")));
        verify(notifications).notifyRole("ADMIN", "customer-maker", "CUSTOMERS", "CUSTOMER_STATUS",
                41L, "PENDING", "Customer status change awaiting approval",
                "A customer status change has been submitted for review");
    }

    @Test
    void administratorCanApproveAnotherUsersPendingRequestAndAuditTheStatusChange() {
        setAdmin("customer-checker");
        CustomerStatusRequest request = request("customer-maker");
        when(requests.findLocked(41L)).thenReturn(Optional.of(request));
        when(audits.save(any(CustomerAuditHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerStatusRequestView result = service.decide(7L, 41L, new CustomerStatusDecision(true));

        assertEquals("APPROVED", result.requestStatus());
        assertEquals("BLOCKED", customer.getSts());
        assertEquals("customer-checker", request.getReviewedBy());
        verify(customers).save(customer);
        verify(audits).save(argThat(audit -> "ACTIVE".equals(audit.getOldVal())
                && "BLOCKED".equals(audit.getNewVal())
                && "customer-checker".equals(audit.getChangedBy())));
        verify(notifications).notifyUser("customer-maker", "CUSTOMERS", "CUSTOMER_STATUS", 41L,
                "DECIDED", "Customer status request decided", "Your customer status change was approved");
    }

    @Test
    void administratorCanRejectWithoutChangingCustomerStatus() {
        setAdmin("customer-checker");
        CustomerStatusRequest request = request("customer-maker");
        when(requests.findLocked(41L)).thenReturn(Optional.of(request));

        CustomerStatusRequestView result = service.decide(7L, 41L, new CustomerStatusDecision(false));

        assertEquals("REJECTED", result.requestStatus());
        assertEquals("ACTIVE", customer.getSts());
        verify(customers, never()).save(any(Customer.class));
        verify(audits, never()).save(any(CustomerAuditHistory.class));
    }

    @Test
    void requesterCannotApproveTheirOwnStatusRequest() {
        setAdmin("customer-maker");
        when(requests.findLocked(41L)).thenReturn(Optional.of(request("customer-maker")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.decide(7L, 41L, new CustomerStatusDecision(true)));

        assertEquals(403, error.getStatusCode().value());
        assertEquals("ACTIVE", customer.getSts());
        verify(requests, never()).saveAndFlush(any(CustomerStatusRequest.class));
        verify(audits, never()).save(any(CustomerAuditHistory.class));
    }

    private CustomerStatusRequest request(String requester) {
        CustomerStatusRequest request = new CustomerStatusRequest();
        ReflectionTestUtils.setField(request, "requestId", 41L);
        request.setCustId(7L);
        request.setFromStatus("ACTIVE");
        request.setToStatus("BLOCKED");
        request.setRequestStatus("PENDING");
        request.setRequestedBy(requester);
        request.setReason("Risk review");
        return request;
    }

    private void setMaker(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, "N/A", List.of(new SimpleGrantedAuthority("ROLE_MAKER"))));
    }

    private void setAdmin(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
