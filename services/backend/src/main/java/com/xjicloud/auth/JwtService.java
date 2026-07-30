package com.xjicloud.auth;

import com.xjicloud.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UserAccount user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.expirationMs());
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("type", "user")
                .claim("username", user.getUsername())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public String generateAdminToken(UUID adminId, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.expirationMs());
        return Jwts.builder()
                .subject(adminId.toString())
                .claim("type", "admin")
                .claim("username", username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public String generateWorkerToken(UUID workerId, String workerName) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.expirationMs() * 7);
        return Jwts.builder()
                .subject(workerId.toString())
                .claim("type", "worker")
                .claim("name", workerName)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public UUID parseUserId(String token) {
        Claims claims = parseClaims(token);
        if (!"user".equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("Invalid user token");
        }
        return UUID.fromString(claims.getSubject());
    }

    public UUID parseAdminId(String token) {
        Claims claims = parseClaims(token);
        if (!"admin".equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("Invalid admin token");
        }
        return UUID.fromString(claims.getSubject());
    }

    public UUID parseWorkerId(String token) {
        Claims claims = parseClaims(token);
        if (!"worker".equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("Invalid worker token");
        }
        return UUID.fromString(claims.getSubject());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationMs() {
        return jwtProperties.expirationMs();
    }
}
