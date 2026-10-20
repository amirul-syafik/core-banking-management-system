package com.cbms.config;

import com.cbms.service.ApplicationActivityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApplicationActivityAuditInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ApplicationActivityAuditInterceptor.class);
    private final ApplicationActivityAuditService service;

    public ApplicationActivityAuditInterceptor(ApplicationActivityAuditService service) {
        this.service = service;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String method = request.getMethod();
        boolean logRead = method.equals("GET") &&
                (request.getRequestURI().equals(request.getContextPath() + "/api/admin/logs") ||
                        request.getRequestURI().startsWith(request.getContextPath() + "/api/admin/logs/"));
        if (!(method.equals("POST") || method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE") || logRead))
            return;
        String path = request.getRequestURI();
        if (!path.startsWith(request.getContextPath() + "/api/") || path.contains("/auth/") || path.contains("/admin/activity-audit"))
            return;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return;
        try {
            service.record(auth.getName(), method, path, response.getStatus());
        } catch (Exception error) {
            log.error("Unable to persist application activity audit; method={} status={}", method, response.getStatus(), error);
        }
    }
}
