package com.xjicloud.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.xjicloud.auth.JwtService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AdminJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AdminUserRepository adminUserRepository;

    public AdminJwtAuthenticationFilter(JwtService jwtService, AdminUserRepository adminUserRepository) {
        this.jwtService = jwtService;
        this.adminUserRepository = adminUserRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/admin/") || path.startsWith("/api/v1/admin/auth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                UUID adminId = jwtService.parseAdminId(token);
                adminUserRepository.findById(adminId).ifPresent(admin -> {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            admin,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
