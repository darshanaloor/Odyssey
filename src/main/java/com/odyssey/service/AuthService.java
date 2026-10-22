package com.odyssey.service;

import com.odyssey.dto.request.ChangePasswordRequest;
import com.odyssey.dto.request.LoginRequest;
import com.odyssey.dto.request.RegisterRequest;
import com.odyssey.dto.request.UpdateProfileRequest;
import com.odyssey.dto.response.UserResponse;
import com.odyssey.entity.UserEntity;
import com.odyssey.exception.ConflictException;
import com.odyssey.exception.RateLimitException;
import com.odyssey.repository.jpa.UserJpaRepository;
import com.odyssey.util.IdGenerator;
import com.odyssey.util.NotificationMasker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserJpaRepository userJpaRepository;
    private final PasswordEncoder passwordEncoder;

    // Rate limiting: 5 failed attempts per 15 minutes per email
    private final ConcurrentMap<String, List<Instant>> failedAttempts = new ConcurrentHashMap<>();
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userJpaRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("An account with this email already exists");
        }

        validatePassword(request.getPassword());

        String userId = IdGenerator.generate("USR");
        String passwordHash = passwordEncoder.encode(request.getPassword());

        UserEntity user = UserEntity.builder()
                .id(userId)
                .name(request.getName().trim())
                .email(normalizedEmail)
                .passwordHash(passwordHash)
                .phone(request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone().trim() : null)
                .createdAt(LocalDateTime.now())
                .build();

        UserEntity saved = userJpaRepository.save(user);
        log.info("Registered new user: id={}, email={}", saved.getId(), NotificationMasker.maskEmail(saved.getEmail()));
        return toResponse(saved);
    }

    public UserResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        checkRateLimit(normalizedEmail);

        Optional<UserEntity> userOpt = userJpaRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty() || !passwordEncoder.matches(request.getPassword(), userOpt.get().getPasswordHash())) {
            recordFailedAttempt(normalizedEmail);
            throw new BadCredentialsException("Invalid email or password");
        }

        UserEntity user = userOpt.get();
        // Clear failed attempts on successful login
        failedAttempts.remove(normalizedEmail);

        // Set up Spring Security session
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        Authentication authentication = new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        log.info("User logged in successfully: email={}", NotificationMasker.maskEmail(user.getEmail()));
        return toResponse(user);
    }

    public void logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @Transactional(readOnly = true)
    public UserEntity getCurrentUserEntity() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String name = auth.getName();
        return userJpaRepository.findByEmail(name)
                .or(() -> userJpaRepository.findById(name))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        UserEntity user = getCurrentUserEntity();
        if (user == null) {
            return null;
        }
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request) {
        UserEntity user = getCurrentUserEntity();
        if (user == null) {
            throw new BadCredentialsException("Authentication required");
        }
        user.setName(request.getName().trim());
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        UserEntity saved = userJpaRepository.save(user);
        return toResponse(saved);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        UserEntity user = getCurrentUserEntity();
        if (user == null) {
            throw new BadCredentialsException("Authentication required");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid current password");
        }

        validatePassword(request.getNewPassword());
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userJpaRepository.save(user);
        log.info("Password changed for user id={}", user.getId());
    }

    private void checkRateLimit(String email) {
        Instant now = Instant.now();
        List<Instant> attempts = failedAttempts.get(email);
        if (attempts != null) {
            synchronized (attempts) {
                attempts.removeIf(timestamp -> Duration.between(timestamp, now).compareTo(LOCKOUT_DURATION) > 0);
                if (attempts.size() >= MAX_FAILED_ATTEMPTS) {
                    throw new RateLimitException("Too many failed login attempts. Please try again in 15 minutes.");
                }
            }
        }
    }

    private void recordFailedAttempt(String email) {
        Instant now = Instant.now();
        failedAttempts.compute(email, (k, v) -> {
            List<Instant> list = (v == null) ? Collections.synchronizedList(new ArrayList<>()) : v;
            list.removeIf(timestamp -> Duration.between(timestamp, now).compareTo(LOCKOUT_DURATION) > 0);
            list.add(now);
            return list;
        });
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException("Password must contain at least one letter and one digit");
        }
    }

    public UserResponse toResponse(UserEntity entity) {
        if (entity == null) return null;
        return UserResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
