package com.xjicloud.framework.integration;

import com.xjicloud.framework.config.FrameworkProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class IntegrationSecretFilter extends OncePerRequestFilter {

    private final FrameworkProperties properties;

    public IntegrationSecretFilter(FrameworkProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/integration/")) {
            String secret = request.getHeader("X-Framework-Secret");
            if (secret == null || !secret.equals(properties.apiSecret())) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.getWriter().write("{\"success\":false,\"message\":\"Invalid framework secret\"}");
                return;
            }
        }
        if (path.startsWith("/api/v1/agent/")) {
            String token = request.getHeader("X-Agent-Token");
            if (token == null || !token.equals(properties.agentToken())) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.getWriter().write("{\"success\":false,\"message\":\"Invalid agent token\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
