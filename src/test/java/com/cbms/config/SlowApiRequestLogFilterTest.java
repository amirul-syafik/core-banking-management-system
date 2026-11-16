package com.cbms.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class SlowApiRequestLogFilterTest {
    @Test
    void usesFiniteCategoriesAndNeverLogsRawPathOrUserId() {
        assertEquals("islamic", SlowApiRequestLogFilter.moduleCategory("/api/islamic/deposits/999/verification"));
        assertEquals("notifications", SlowApiRequestLogFilter.moduleCategory("/api/notifications/12345/read"));
        assertEquals("other", SlowApiRequestLogFilter.moduleCategory("/api/secret-customer-data/123"));
        assertEquals("other", SlowApiRequestLogFilter.moduleCategory("/customer/123"));
        assertEquals("other", SlowApiRequestLogFilter.moduleCategory(null));
        assertEquals("GET", SlowApiRequestLogFilter.safeMethod("GET"));
        assertEquals("OTHER", SlowApiRequestLogFilter.safeMethod("CUSTOM\nINJECT"));
        assertEquals("OTHER", SlowApiRequestLogFilter.safeMethod(null));
    }

    @Test
    void monitoringDoesNotModifyApiResponse() throws Exception {
        SlowApiRequestLogFilter filter = new SlowApiRequestLogFilter(0);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/islamic/deposits/10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertSame(request, chain.getRequest());
        assertSame(response, chain.getResponse());
        assertEquals(200, response.getStatus());
    }
}
