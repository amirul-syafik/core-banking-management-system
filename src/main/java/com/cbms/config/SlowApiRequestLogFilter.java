package com.cbms.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Low-cardinality latency signal for performance triage.
 * Never log full URL, query string, customer/transaction IDs, JWT or request body.
 * It does not change the response, rate-limit requests or authorize operations.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class SlowApiRequestLogFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(SlowApiRequestLogFilter.class);
    private static final Set<String> MODULES = Set.of(
            "accounts", "transactions", "reconciliation", "fixed-deposits", "islamic",
            "notifications", "financing", "credit-lines", "customers", "admin",
            "reporting", "dashboard", "auth", "workflow", "products", "schemes");
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private final long thresholdMs;

    public SlowApiRequestLogFilter(@Value("${app.performance.slow-request-ms:2000}") long thresholdMs) {
        this.thresholdMs = Math.max(0L, thresholdMs);
    }

    static String moduleCategory(String requestUri) {
        if (requestUri == null || !requestUri.startsWith("/api/")) return "other";
        String tail = requestUri.substring("/api/".length());
        int end = tail.indexOf('/');
        String first = end < 0 ? tail : tail.substring(0, end);
        return MODULES.contains(first) ? first : "other";
    }

    static String safeMethod(String method) {
        return method != null && METHODS.contains(method) ? method : "OTHER";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return thresholdMs == 0 || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            if (elapsed >= thresholdMs) {
                log.warn("Slow API request module={} method={} status={} durationMs={}",
                        moduleCategory(request.getRequestURI()), safeMethod(request.getMethod()),
                        response.getStatus(), elapsed);
            }
        }
    }
}
