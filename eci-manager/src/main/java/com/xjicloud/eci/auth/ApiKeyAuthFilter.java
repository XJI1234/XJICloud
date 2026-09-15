package com.xjicloud.eci.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjicloud.eci.common.ApiResponse;
import com.xjicloud.eci.credential.HttpApiKeyStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 校验请求头 {@code X-Api-Key} 是否与 {@link HttpApiKeyStore} 当前钥（或重叠期内上一把）一致。
 * <p>
 * 仅拦截 {@code /api/v1/eci/**}；健康检查等路径不经过本过滤器。
 * 校验失败返回 401 与统一 {@link ApiResponse} 失败体，不执行后续业务（避免误创建云资源）。
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    /** 客户端须携带的请求头名称。 */
    public static final String HEADER_API_KEY = "X-Api-Key";

    private final HttpApiKeyStore httpApiKeyStore;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(HttpApiKeyStore httpApiKeyStore, ObjectMapper objectMapper) {
        this.httpApiKeyStore = httpApiKeyStore;
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
        if (!httpApiKeyStore.hasCurrent()) {
            writeUnauthorized(response, "服务器未配置 eci.api-key");
            return;
        }

        String provided = request.getHeader(HEADER_API_KEY);
        if (!httpApiKeyStore.accepts(provided)) {
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
}
