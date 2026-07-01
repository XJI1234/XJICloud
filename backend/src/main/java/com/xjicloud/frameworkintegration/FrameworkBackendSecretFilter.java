package com.xjicloud.frameworkintegration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FrameworkBackendSecretFilter extends OncePerRequestFilter {

    private final FrameworkIntegrationProperties properties;

    public FrameworkBackendSecretFilter(FrameworkIntegrationProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("/api/v1/framework/queue-stats".equals(request.getRequestURI())
                && "GET".equalsIgnoreCase(request.getMethod())) {
            String secret = request.getHeader("X-Framework-Secret");
            if (properties.backendApiSecret() != null
                    && !properties.backendApiSecret().isBlank()
                    && (secret == null || !secret.equals(properties.backendApiSecret()))) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.getWriter().write("{\"success\":false,\"message\":\"Invalid secret\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
