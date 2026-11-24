package com.accesolibre.security;

import com.accesolibre.config.AppProperties;
import com.accesolibre.domain.entity.User;
import com.accesolibre.domain.enums.UserRole;
import com.accesolibre.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class JwtService {

    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private static final String CLAIM_CONDO_ID = "condoId";
    private static final String CLAIM_UNIT_ID = "unitId";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";

    private final AppProperties properties;
    private final SecretKey signingKey;

    public JwtService(AppProperties properties) {
        this.properties = properties;
        byte[] keyBytes = deriveKeyBytes(properties.getSecurity().getJwtSecret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user) {
        return generateToken(user, TOKEN_TYPE_ACCESS, properties.getSecurity().getAccessTokenTtlSeconds());
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, TOKEN_TYPE_REFRESH, properties.getSecurity().getRefreshTokenTtlSeconds());
    }

    public AuthenticatedUser parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!TOKEN_TYPE_ACCESS.equals(tokenType)) {
            throw new UnauthorizedException("Invalid token type: expected access token");
        }
        return toAuthenticatedUser(claims);
    }

    public AuthenticatedUser parseRefreshToken(String token) {
        Claims claims = parseClaims(token);
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!TOKEN_TYPE_REFRESH.equals(tokenType)) {
            throw new UnauthorizedException("Invalid token type: expected refresh token");
        }
        return toAuthenticatedUser(claims);
    }

    public long getAccessTokenTtlSeconds() {
        return properties.getSecurity().getAccessTokenTtlSeconds();
    }

    public long getRefreshTokenTtlSeconds() {
        return properties.getSecurity().getRefreshTokenTtlSeconds();
    }

    public String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    private String generateToken(User user, String tokenType, long ttlSeconds) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);

        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_CONDO_ID, user.getCondominiumId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TOKEN_TYPE, tokenType);

        if (user.getUnitId() != null) {
            builder.claim(CLAIM_UNIT_ID, user.getUnitId().toString());
        }

        return builder.signWith(signingKey, Jwts.SIG.HS256).compact();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid or expired JWT token");
        }
    }

    private AuthenticatedUser toAuthenticatedUser(Claims claims) {
        try {
            UUID userId = UUID.fromString(claims.getSubject());
            UUID condoId = UUID.fromString(claims.get(CLAIM_CONDO_ID, String.class));
            String unitIdRaw = claims.get(CLAIM_UNIT_ID, String.class);
            UUID unitId = (unitIdRaw != null && !unitIdRaw.isBlank()) ? UUID.fromString(unitIdRaw) : null;
            String email = claims.get(CLAIM_EMAIL, String.class);
            UserRole role = UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));
            return new AuthenticatedUser(userId, condoId, unitId, email, role);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new UnauthorizedException("Malformed JWT claims");
        }
    }

    private byte[] deriveKeyBytes(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException("accesolibre.security.jwt-secret must be configured");
        }
        byte[] raw = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (raw.length >= 32) {
            return raw;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
