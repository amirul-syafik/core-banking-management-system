package com.cbms.service;

import com.cbms.dto.CustomerStatusUpdateRequest;
import com.cbms.entity.Customer;
import com.cbms.entity.CustomerAuditHistory;
import com.cbms.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import com.cbms.repository.CustomerAuditHistoryRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerStatusServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerAuditHistoryRepository auditRepository;

    @InjectMocks
    private CustomerStatusService customerStatusService;

    @Test
    void shouldActivatePendingCustomer() {
        Customer customer = createCustomer("PENDING");

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));
        when(customerRepository.save(customer))
                .thenReturn(customer);
        when(auditRepository.save(any(CustomerAuditHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CustomerStatusUpdateRequest request = new CustomerStatusUpdateRequest();
        request.setSts("ACTIVE");

        Customer result =
                customerStatusService.updateStatus(1L, request);

        assertEquals("ACTIVE", result.getSts());
        assertNotNull(result.getUpdDt());
        assertEquals("SYSTEM", result.getUpdBy());

        verify(customerRepository).save(customer);
        verify(auditRepository).save(any(CustomerAuditHistory.class));
    }

    @Test
    void shouldBlockActiveCustomer() {
        Customer customer = createCustomer("ACTIVE");

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));
        when(customerRepository.save(customer))
                .thenReturn(customer);
        when(auditRepository.save(any(CustomerAuditHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CustomerStatusUpdateRequest request = new CustomerStatusUpdateRequest();
        request.setSts("BLOCKED");

        Customer result =
                customerStatusService.updateStatus(1L, request);

        assertEquals("BLOCKED", result.getSts());
        verify(customerRepository).save(customer);
        verify(auditRepository).save(any(CustomerAuditHistory.class));
    }

    @Test
    void shouldRejectActivationOfBlockedCustomer() {
        Customer customer = createCustomer("BLOCKED");

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        CustomerStatusUpdateRequest request = new CustomerStatusUpdateRequest();
        request.setSts("ACTIVE");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> customerStatusService.updateStatus(1L, request)
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(customerRepository, never()).save(any(Customer.class));
        verify(auditRepository, never()).save(any(CustomerAuditHistory.class));
    }

    @Test
    void shouldRejectUnchangedStatus() {
        Customer customer = createCustomer("ACTIVE");

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        CustomerStatusUpdateRequest request = new CustomerStatusUpdateRequest();
        request.setSts("ACTIVE");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> customerStatusService.updateStatus(1L, request)
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(customerRepository, never()).save(any(Customer.class));
        verify(auditRepository, never()).save(any(CustomerAuditHistory.class));
    }

    @Test
    void shouldReturnNotFoundForMissingCustomer() {
        when(customerRepository.findById(999L))
                .thenReturn(Optional.empty());

        CustomerStatusUpdateRequest request = new CustomerStatusUpdateRequest();
        request.setSts("ACTIVE");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> customerStatusService.updateStatus(999L, request)
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(customerRepository, never()).save(any(Customer.class));
        verify(auditRepository, never()).save(any(CustomerAuditHistory.class));
    }

    private Customer createCustomer(String status) {
        Customer customer = new Customer();
        customer.setSts(status);
        return customer;
    }
}
