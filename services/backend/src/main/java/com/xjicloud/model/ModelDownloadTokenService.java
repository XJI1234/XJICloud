package com.xjicloud.model;

import com.xjicloud.auth.UserAccount;
import com.xjicloud.common.BusinessException;
import com.xjicloud.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ModelDownloadTokenService {

    private static final String PURPOSE = "model-download";
    private static final long TOKEN_TTL_MS = 15 * 60 * 1000L;

    private final SecretKey secretKey;

    public ModelDownloadTokenService(JwtProperties jwtProperties) {
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public DownloadTokenDetails createToken(UserAccount user, UUID modelId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusMillis(TOKEN_TTL_MS);
        String token = Jwts.builder()
                .subject(user.getId().toString())
                .claim("modelId", modelId.toString())
                .claim("purpose", PURPOSE)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
        return new DownloadTokenDetails(token, expiresAt);
    }

    public UUID validateToken(String token, UUID modelId) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("下载令牌无效", HttpStatus.UNAUTHORIZED);
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!PURPOSE.equals(claims.get("purpose", String.class))) {
                throw new BusinessException("下载令牌无效", HttpStatus.UNAUTHORIZED);
            }

            String claimModelId = claims.get("modelId", String.class);
            if (claimModelId == null || !claimModelId.equals(modelId.toString())) {
                throw new BusinessException("下载令牌与模型不匹配", HttpStatus.FORBIDDEN);
            }

            return UUID.fromString(claims.getSubject());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("下载令牌无效或已过期", HttpStatus.UNAUTHORIZED);
        }
    }

    public record DownloadTokenDetails(String token, Instant expiresAt) {
    }
}
