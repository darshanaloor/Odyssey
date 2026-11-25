package com.odyssey.security;

import com.odyssey.entity.UserEntity;
import com.odyssey.model.enums.UserRole;
import com.odyssey.repository.jpa.UserJpaRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenAuthService {

    private final UserJpaRepository userJpaRepository;

    private static final Duration TOKEN_EXPIRY = Duration.ofHours(24);
    private static final String DEFAULT_HMAC_SECRET = "odyssey-multi-tab-isolated-hmac-secret-2026";
    private final ConcurrentMap<String, TokenSession> activeSessions = new ConcurrentHashMap<>();
    private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();

    @Data
    @Builder
    public static class TokenSession {
        private String token;
        private String userId;
        private String email;
        private UserRole role;
        private List<GrantedAuthority> authorities;
        private Instant createdAt;
        private Instant lastAccessedAt;
    }

    /**
     * Creates and registers a new HMAC-signed tab/client session token for the user.
     * Survives server restarts while still supporting per-tab logout revocation.
     */
    public String createToken(UserEntity user, List<GrantedAuthority> authorities) {
        Instant now = Instant.now();
        UserRole role = user.getRole() != null ? user.getRole() : UserRole.TRAVELLER;
        String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String payload = user.getId() + "|" + role.name() + "|" + now.getEpochSecond() + "|" + nonce;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String signature = signHmac(encodedPayload);
        String token = "TKN-" + encodedPayload + "." + signature;

        List<GrantedAuthority> authList = new ArrayList<>(authorities);
        TokenSession session = TokenSession.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .role(role)
                .authorities(authList)
                .createdAt(now)
                .lastAccessedAt(now)
                .build();

        activeSessions.put(token, session);
        log.debug("Created auth token for user {}: {}", user.getEmail(), token);
        cleanExpiredSessions();
        return token;
    }

    /**
     * Resolves the Spring Security Authentication object for a given token.
     */
    public Authentication getAuthentication(String token) {
        TokenSession session = resolveOrRestoreSession(token);
        if (session == null) {
            return null;
        }
        return new UsernamePasswordAuthenticationToken(session.getEmail(), null, session.getAuthorities());
    }

    /**
     * Resolves the UserEntity for a given token.
     */
    public UserEntity getUser(String token) {
        TokenSession session = resolveOrRestoreSession(token);
        if (session == null) {
            return null;
        }
        return userJpaRepository.findByEmail(session.getEmail())
                .or(() -> userJpaRepository.findById(session.getUserId()))
                .orElse(null);
    }

    /**
     * Resolves token session metadata if needed.
     */
    public TokenSession getSession(String token) {
        return resolveOrRestoreSession(token);
    }

    /**
     * Invalidates a specific token (e.g. upon tab logout).
     */
    public void invalidateToken(String token) {
        if (token != null && !token.isBlank()) {
            String trimmed = token.trim();
            activeSessions.remove(trimmed);
            revokedTokens.add(trimmed);
            log.debug("Auth token invalidated: {}", trimmed);
        }
    }

    private TokenSession resolveOrRestoreSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        String token = rawToken.trim();
        if (revokedTokens.contains(token)) {
            return null;
        }

        Instant now = Instant.now();
        TokenSession session = activeSessions.get(token);
        if (session != null) {
            if (Duration.between(session.getLastAccessedAt(), now).compareTo(TOKEN_EXPIRY) > 0) {
                activeSessions.remove(token);
                log.debug("Auth token expired and removed: {}", token);
                return null;
            }
            session.setLastAccessedAt(now);
            return session;
        }

        // Attempt stateless HMAC verification (allows tabs to survive server restarts without JSESSIONID bleed)
        if (!token.startsWith("TKN-") || !token.contains(".")) {
            return null;
        }
        try {
            String body = token.substring(4);
            int dotIdx = body.indexOf('.');
            if (dotIdx <= 0) {
                return null;
            }
            String encodedPayload = body.substring(0, dotIdx);
            String providedSig = body.substring(dotIdx + 1);
            String expectedSig = signHmac(encodedPayload);
            if (!java.security.MessageDigest.isEqual(
                    providedSig.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    expectedSig.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                return null;
            }

            String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), java.nio.charset.StandardCharsets.UTF_8);
            String[] parts = payload.split("\\|");
            if (parts.length < 3) {
                return null;
            }
            String userId = parts[0];
            long issuedEpoch = Long.parseLong(parts[2]);
            Instant issuedAt = Instant.ofEpochSecond(issuedEpoch);
            if (Duration.between(issuedAt, now).compareTo(TOKEN_EXPIRY) > 0) {
                return null;
            }

            UserEntity user = userJpaRepository.findById(userId).orElse(null);
            if (user == null) {
                return null;
            }
            UserRole role = user.getRole() != null ? user.getRole() : UserRole.TRAVELLER;
            List<GrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("ROLE_" + role.name())
            );
            TokenSession restored = TokenSession.builder()
                    .token(token)
                    .userId(user.getId())
                    .email(user.getEmail())
                    .role(role)
                    .authorities(authorities)
                    .createdAt(issuedAt)
                    .lastAccessedAt(now)
                    .build();
            activeSessions.put(token, restored);
            return restored;
        } catch (Exception e) {
            return null;
        }
    }

    private String signHmac(String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(
                    DEFAULT_HMAC_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] raw = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign auth token", e);
        }
    }

    private void cleanExpiredSessions() {
        if (activeSessions.size() > 500) {
            Instant cutoff = Instant.now().minus(TOKEN_EXPIRY);
            activeSessions.entrySet().removeIf(e -> e.getValue().getLastAccessedAt().isBefore(cutoff));
        }
    }
}
