package com.cbms.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Generates a server-owned request ID, present in both log appenders and error responses. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".requestId";
    public static final String ERROR_RECORDED = RequestCorrelationFilter.class.getName() + ".logged";
    public static final String HEADER = "X-Request-ID";
    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader(HEADER, id);
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", id)) {
            try {
                chain.doFilter(request, response);
            } catch (IOException | ServletException | RuntimeException error) {
                // Logging only the exception *type* avoids leaking query parameters,
                // credentials, payloads, or sensitive database details in exception messages.
                if (request.getAttribute(ERROR_RECORDED) == null) {
                    log.error("Unhandled request failure method={} exceptionType={}",
                            request.getMethod(), error.getClass().getSimpleName());
                    request.setAttribute(ERROR_RECORDED, Boolean.TRUE);
                }
                throw error;
            } finally {
                // Security-filter-generated 401/403 and other unhandled HTTP errors also
                // appear in CBMS Application Logs, without logging a raw customer URL.
                if (response.getStatus() >= 400 && request.getAttribute(ERROR_RECORDED) == null) {
                    log.warn("HTTP request rejected method={} status={}", request.getMethod(), response.getStatus());
                }
            }
        }
    }
}
