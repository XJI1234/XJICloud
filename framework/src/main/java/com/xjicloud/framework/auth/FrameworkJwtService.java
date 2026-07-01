package com.xjicloud.framework.auth;

import com.xjicloud.framework.config.FrameworkProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class FrameworkJwtService {

    private final SecretKey secretKey;
    private final long expirationMs = 86400000L;

    public FrameworkJwtService(FrameworkProperties properties) {
        String secret = properties.jwtSecret();
        if (secret == null || secret.length() < 32) {
            secret = "change-me-framework-jwt-secret-at-least-32-chars";
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UUID userId, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "framework-admin")
                .claim("username", username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public UUID parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!"framework-admin".equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("Invalid token");
        }
        return UUID.fromString(claims.getSubject());
    }
}
