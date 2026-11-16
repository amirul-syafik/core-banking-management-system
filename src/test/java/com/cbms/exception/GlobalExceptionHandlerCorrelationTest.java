package com.cbms.exception;

import com.cbms.config.RequestCorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerCorrelationTest {
    @Test
    void responsePreservesHttpStatusAndAddsRequestId() {
        var request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/financing/1234");
        when(request.getMethod()).thenReturn("POST");
        when(request.getAttribute(RequestCorrelationFilter.ATTRIBUTE)).thenReturn("test-request-id");

        var response = new GlobalExceptionHandler().handleResponseStatusException(
                new ResponseStatusException(HttpStatus.CONFLICT, "Eligibility check failed"), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Eligibility check failed", response.getBody().getMessage());
        assertEquals("test-request-id", response.getBody().getRequestId());
        verify(request).setAttribute(RequestCorrelationFilter.ERROR_RECORDED, Boolean.TRUE);
    }
}
