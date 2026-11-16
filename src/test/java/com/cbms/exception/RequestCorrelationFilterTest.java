package com.cbms.exception;

import com.cbms.config.RequestCorrelationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RequestCorrelationFilterTest {
    @Test
    void responseAndMdcUseServerGeneratedRequestId() throws Exception {
        var filter = new RequestCorrelationFilter();
        var request = new MockHttpServletRequest("GET", "/api/admin/logs");
        var response = new MockHttpServletResponse();
        request.addHeader(RequestCorrelationFilter.HEADER, "unsafe-client-id");
        FilterChain chain = (req, resp) -> {
            assertEquals(req.getAttribute(RequestCorrelationFilter.ATTRIBUTE), org.slf4j.MDC.get("requestId"));
        };

        filter.doFilter(request, response, chain);
        String id = response.getHeader(RequestCorrelationFilter.HEADER);
        assertNotNull(id);
        assertNotEquals("unsafe-client-id", id);
        assertEquals(id, UUID.fromString(id).toString());
        assertNull(org.slf4j.MDC.get("requestId"));
    }
}
