package com.bloodlink.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKeyString;

    @Value("${jwt.expiration-ms:604800000}")
    private long jwtExpirationMs = 604800000L;

    public JwtService() {}

    public JwtService(String secretKeyString, long jwtExpirationMs) {
        this.secretKeyString = secretKeyString;
        this.jwtExpirationMs = jwtExpirationMs;
    }

    @PostConstruct
    public void validateConfiguration() {
        if (secretKeyString == null || secretKeyString.trim().isEmpty()) {
            throw new IllegalStateException("JWT signing key is not configured. Please set the JWT_SECRET environment variable or 'jwt.secret' property.");
        }
    }

    private SecretKey getSigningKey() {
        if (secretKeyString == null || secretKeyString.trim().isEmpty()) {
            throw new IllegalStateException("JWT signing key is missing. Please set the JWT_SECRET environment variable or 'jwt.secret' property.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secretKeyString);
        } catch (Exception e) {
            keyBytes = secretKeyString.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String userId, String role, String email) {
        Map<String, Object> extraClaims = new HashMap<>();
        if (role != null) {
            extraClaims.put("role", role.toLowerCase().trim());
        }
        if (email != null) {
            extraClaims.put("email", email.toLowerCase().trim());
        }
        return buildToken(extraClaims, userId, jwtExpirationMs);
    }

    public String generateTokenWithExpiration(String userId, String role, String email, long expirationMs) {
        Map<String, Object> extraClaims = new HashMap<>();
        if (role != null) {
            extraClaims.put("role", role.toLowerCase().trim());
        }
        if (email != null) {
            extraClaims.put("email", email.toLowerCase().trim());
        }
        return buildToken(extraClaims, userId, expirationMs);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        long nowMillis = System.currentTimeMillis();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(nowMillis))
                .expiration(new Date(nowMillis + expiration))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims != null && !isTokenExpired(claims);
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration != null && expiration.before(new Date());
    }

    public long getJwtExpirationMs() {
        return jwtExpirationMs;
    }
}
