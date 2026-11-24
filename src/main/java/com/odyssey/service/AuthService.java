package com.odyssey.service;

import com.odyssey.dto.provider.ProviderSignupRequest;
import com.odyssey.dto.request.ChangePasswordRequest;
import com.odyssey.dto.request.LoginRequest;
import com.odyssey.dto.request.RegisterRequest;
import com.odyssey.dto.request.UpdateProfileRequest;
import com.odyssey.dto.response.UserResponse;
import com.odyssey.entity.AuditEntryEntity;
import com.odyssey.entity.ProviderEntity;
import com.odyssey.entity.UserEntity;
import com.odyssey.exception.ConflictException;
import com.odyssey.exception.RateLimitException;
import com.odyssey.model.CityInfo;
import com.odyssey.model.enums.ProviderStatus;
import com.odyssey.model.enums.UserRole;
import com.odyssey.repository.jpa.AuditEntryJpaRepository;
import com.odyssey.repository.jpa.ProviderJpaRepository;
import com.odyssey.repository.jpa.UserJpaRepository;
import com.odyssey.util.EmergencyContactUtil;
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
    private final ProviderJpaRepository providerJpaRepository;
    private final AuditEntryJpaRepository auditEntryJpaRepository;
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

    @Transactional
    public UserResponse registerProvider(ProviderSignupRequest request, HttpServletRequest httpRequest) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userJpaRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("An account with this email already exists");
        }

        validatePassword(request.getPassword());

        // Validate city in cities.json
        CityInfo cityInfo = InventoryService.findCity(request.getCity());
        boolean cityMatches = cityInfo != null && (
                cityInfo.getName().equalsIgnoreCase(request.getCity().trim())
                || cityInfo.getKey().equalsIgnoreCase(request.getCity().trim())
                || (cityInfo.getAliases() != null && cityInfo.getAliases().stream().anyMatch(a -> a.equalsIgnoreCase(request.getCity().trim())))
        );
        if (!cityMatches) {
            throw new IllegalArgumentException("City '" + request.getCity() + "' is not supported in the Odyssey network");
        }

        // Validate and normalize contact phone
        String normalizedPhone = EmergencyContactUtil.normalizePhone(request.getContactPhone());

        String contactEmail = (request.getContactEmail() != null && !request.getContactEmail().isBlank())
                ? request.getContactEmail().trim().toLowerCase()
                : normalizedEmail;

        String userId = IdGenerator.generate("USR");
        String passwordHash = passwordEncoder.encode(request.getPassword());

        UserEntity user = UserEntity.builder()
                .id(userId)
                .name(request.getName().trim())
                .email(normalizedEmail)
                .passwordHash(passwordHash)
                .phone(normalizedPhone)
                .role(UserRole.PROVIDER)
                .createdAt(LocalDateTime.now())
                .build();

        UserEntity savedUser = userJpaRepository.save(user);

        String providerId = IdGenerator.generate("PRV");
        ProviderEntity provider = ProviderEntity.builder()
                .id(providerId)
                .user(savedUser)
                .businessName(request.getBusinessName().trim())
                .businessType(request.getBusinessType())
                .contactEmail(contactEmail)
                .contactPhone(normalizedPhone)
                .city(cityInfo.getName())
                .description(request.getDescription() != null && !request.getDescription().isBlank() ? request.getDescription().trim() : null)
                .status(ProviderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ProviderEntity savedProvider = providerJpaRepository.save(provider);

        if (auditEntryJpaRepository != null) {
            auditEntryJpaRepository.save(AuditEntryEntity.builder()
                    .id(IdGenerator.generate("AUD"))
                    .timestamp(LocalDateTime.now())
                    .actor("USER")
                    .action("PROVIDER_REGISTERED")
                    .details("Brand-new provider registered: " + savedProvider.getBusinessName() + " (ID: " + savedProvider.getId() + ")")
                    .build());
        }

        log.info("Registered new provider account atomically: userId={}, providerId={}, businessName={}, email={}",
                savedUser.getId(), savedProvider.getId(), savedProvider.getBusinessName(), NotificationMasker.maskEmail(savedUser.getEmail()));

        // Set up Spring Security session so user is logged in
        if (httpRequest != null) {
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            authorities.add(new SimpleGrantedAuthority("ROLE_PROVIDER"));

            Authentication authentication = new UsernamePasswordAuthenticationToken(savedUser.getEmail(), null, authorities);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        }

        return toResponse(savedUser);
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
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (user.getRole() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_TRAVELLER"));
        }
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

    public void refreshAuthenticationRole(UserEntity user, HttpServletRequest httpRequest) {
        if (user == null) return;
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (user.getRole() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_TRAVELLER"));
        }
        Authentication authentication = new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        if (httpRequest != null) {
            HttpSession session = httpRequest.getSession(false);
            if (session != null) {
                session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            }
        }
    }

    public UserResponse toResponse(UserEntity entity) {
        if (entity == null) return null;
        return UserResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .role(entity.getRole() != null ? entity.getRole().name() : "TRAVELLER")
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
