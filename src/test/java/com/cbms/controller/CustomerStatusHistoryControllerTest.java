package com.cbms.controller;

import com.cbms.repository.CustomerStatusRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CustomerStatusHistoryControllerTest {

    @Test
    void timestampDirectionSelectsMatchingServerQuery() {
        CustomerStatusRequestRepository repository = mock(CustomerStatusRequestRepository.class);
        when(repository.findHistoryByEventTimestampAscending(any(Pageable.class))).thenReturn(Page.empty());
        when(repository.findHistoryByEventTimestampDescending(any(Pageable.class))).thenReturn(Page.empty());
        CustomerStatusHistoryController controller = new CustomerStatusHistoryController(repository);

        controller.list(0, 10, "asc");
        controller.list(0, 10, "DESC");

        verify(repository).findHistoryByEventTimestampAscending(any(Pageable.class));
        verify(repository).findHistoryByEventTimestampDescending(any(Pageable.class));
    }

    @Test
    void missingTimestampDirectionKeepsExistingDefaultQuery() {
        CustomerStatusRequestRepository repository = mock(CustomerStatusRequestRepository.class);
        when(repository.findHistory(any(Pageable.class))).thenReturn(Page.empty());

        new CustomerStatusHistoryController(repository).list(0, 10, null);

        verify(repository).findHistory(any(Pageable.class));
    }

    @Test
    void invalidTimestampDirectionIsRejected() {
        CustomerStatusRequestRepository repository = mock(CustomerStatusRequestRepository.class);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> new CustomerStatusHistoryController(repository).list(0, 10, "sideways"));

        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(repository);
    }
}
