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
    private final ConcurrentMap<String, TokenSession> activeSessions = new ConcurrentHashMap<>();

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
     * Creates and registers a new tab/client session token for the user.
     */
    public String createToken(UserEntity user, List<GrantedAuthority> authorities) {
        String token = "TKN-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        Instant now = Instant.now();

        List<GrantedAuthority> authList = new ArrayList<>(authorities);
        TokenSession session = TokenSession.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole() : UserRole.TRAVELLER)
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
        if (token == null || token.isBlank()) {
            return null;
        }

        TokenSession session = activeSessions.get(token.trim());
        if (session == null) {
            return null;
        }

        Instant now = Instant.now();
        if (Duration.between(session.getLastAccessedAt(), now).compareTo(TOKEN_EXPIRY) > 0) {
            activeSessions.remove(token.trim());
            log.debug("Auth token expired and removed: {}", token);
            return null;
        }

        session.setLastAccessedAt(now);
        return new UsernamePasswordAuthenticationToken(session.getEmail(), null, session.getAuthorities());
    }

    /**
     * Resolves the UserEntity for a given token.
     */
    public UserEntity getUser(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        TokenSession session = activeSessions.get(token.trim());
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
        if (token == null || token.isBlank()) {
            return null;
        }
        return activeSessions.get(token.trim());
    }

    /**
     * Invalidates a specific token (e.g. upon tab logout).
     */
    public void invalidateToken(String token) {
        if (token != null && !token.isBlank()) {
            activeSessions.remove(token.trim());
            log.debug("Auth token invalidated: {}", token);
        }
    }

    private void cleanExpiredSessions() {
        if (activeSessions.size() > 500) {
            Instant cutoff = Instant.now().minus(TOKEN_EXPIRY);
            activeSessions.entrySet().removeIf(e -> e.getValue().getLastAccessedAt().isBefore(cutoff));
        }
    }
}
