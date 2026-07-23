package com.xjicloud.eci.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.common.ApiResponse;
import com.xjicloud.eci.config.EciProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_API_KEY = "X-Api-Key";

    private final EciProperties eciProperties;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(EciProperties eciProperties, ObjectMapper objectMapper) {
        this.eciProperties = eciProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/eci/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String configured = eciProperties.getApiKey();
        if (!StringUtils.hasText(configured)) {
            writeUnauthorized(response, "服务器未配置 eci.api-key");
            return;
        }

        String provided = request.getHeader(HEADER_API_KEY);
        if (!StringUtils.hasText(provided) || !constantTimeEquals(configured, provided)) {
            writeUnauthorized(response, "无效或缺失的 Api-Key");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail(message));
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
