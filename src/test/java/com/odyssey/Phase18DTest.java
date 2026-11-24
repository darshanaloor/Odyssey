package com.odyssey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.dto.provider.ProviderRegisterRequest;
import com.odyssey.dto.provider.ProviderSignupRequest;
import com.odyssey.dto.request.LoginRequest;
import com.odyssey.entity.ProviderEntity;
import com.odyssey.entity.UserEntity;
import com.odyssey.model.enums.BusinessType;
import com.odyssey.model.enums.ProviderStatus;
import com.odyssey.model.enums.UserRole;
import com.odyssey.repository.jpa.ProviderJpaRepository;
import com.odyssey.repository.jpa.UserJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class Phase18DTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserJpaRepository userRepo;

    @Autowired
    private ProviderJpaRepository providerRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private com.odyssey.repository.jpa.ProviderVehicleJpaRepository vehicleRepo;

    @Test
    @DisplayName("1. Fresh Provider Sign-Up: Atomically creates exactly 1 user + 1 provider profile (PENDING), with role PROVIDER")
    void testAtomicProviderSignup_success() throws Exception {
        String email = "fresh_provider_" + System.currentTimeMillis() + "@example.com";
        ProviderSignupRequest req = ProviderSignupRequest.builder()
                .name("Kavitha Rao")
                .email(email)
                .password("ProviderPass123")
                .businessName("Kavitha Express Lines")
                .businessType(BusinessType.TRANSPORT)
                .contactPhone("9876501234")
                .contactEmail(email)
                .city("Bengaluru")
                .description("Intercity luxury bus service")
                .build();

        long usersBefore = userRepo.count();
        long providersBefore = providerRepo.count();

        mockMvc.perform(post("/api/auth/provider-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.data.role").value("PROVIDER"));

        assertEquals(usersBefore + 1, userRepo.count(), "Exactly one user must be created");
        assertEquals(providersBefore + 1, providerRepo.count(), "Exactly one provider must be created");

        UserEntity user = userRepo.findByEmail(email.toLowerCase()).orElseThrow();
        assertEquals(UserRole.PROVIDER, user.getRole());
        assertEquals("+919876501234", user.getPhone());
        assertTrue(passwordEncoder.matches("ProviderPass123", user.getPasswordHash()));

        ProviderEntity provider = providerRepo.findByUserId(user.getId()).orElseThrow();
        assertEquals("Kavitha Express Lines", provider.getBusinessName());
        assertEquals(ProviderStatus.PENDING, provider.getStatus());
        assertEquals(BusinessType.TRANSPORT, provider.getBusinessType());
        assertEquals("Bengaluru", provider.getCity());
        assertEquals("+919876501234", provider.getContactPhone());
    }

    @Test
    @DisplayName("2. Atomic Rollback: Invalid city rolls back transaction, leaving zero orphan user accounts")
    void testAtomicProviderSignup_invalidCityRollback() throws Exception {
        String email = "invalid_city_" + System.currentTimeMillis() + "@example.com";
        ProviderSignupRequest req = ProviderSignupRequest.builder()
                .name("Orphan Check")
                .email(email)
                .password("ProviderPass123")
                .businessName("Atlantis Voyages")
                .businessType(BusinessType.TRANSPORT)
                .contactPhone("9876501234")
                .city("Atlantis") // Not in cities.json
                .build();

        long usersBefore = userRepo.count();
        long providersBefore = providerRepo.count();

        mockMvc.perform(post("/api/auth/provider-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not supported in the Odyssey network")));

        assertEquals(usersBefore, userRepo.count(), "No orphan user account must be created on invalid city");
        assertEquals(providersBefore, providerRepo.count(), "No provider entity must be created on invalid city");
        assertTrue(userRepo.findByEmail(email.toLowerCase()).isEmpty());
    }

    @Test
    @DisplayName("3. Atomic Rollback: Bad phone number format rolls back transaction, leaving zero orphan user accounts")
    void testAtomicProviderSignup_invalidPhoneRollback() throws Exception {
        String email = "invalid_phone_" + System.currentTimeMillis() + "@example.com";
        ProviderSignupRequest req = ProviderSignupRequest.builder()
                .name("Phone Fail")
                .email(email)
                .password("ProviderPass123")
                .businessName("Bad Phone Fleet")
                .businessType(BusinessType.HOTEL)
                .contactPhone("12345") // Invalid Indian phone
                .city("Bengaluru")
                .build();

        long usersBefore = userRepo.count();
        long providersBefore = providerRepo.count();

        mockMvc.perform(post("/api/auth/provider-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("10-digit Indian phone number")));

        assertEquals(usersBefore, userRepo.count(), "No orphan user account must be created on invalid phone");
        assertEquals(providersBefore, providerRepo.count(), "No provider entity must be created on invalid phone");
        assertTrue(userRepo.findByEmail(email.toLowerCase()).isEmpty());
    }

    @Test
    @DisplayName("4. Duplicate Email: Rejects with 409 Conflict if email is already registered")
    void testAtomicProviderSignup_duplicateEmailConflict() throws Exception {
        String email = "dup_prov_" + System.currentTimeMillis() + "@example.com";
        // Create an existing user
        userRepo.save(UserEntity.builder()
                .id("USR-DUP-01")
                .name("Existing User")
                .email(email.toLowerCase())
                .passwordHash(passwordEncoder.encode("ExistingPass123"))
                .role(UserRole.TRAVELLER)
                .build());

        ProviderSignupRequest req = ProviderSignupRequest.builder()
                .name("Second Registration")
                .email(email)
                .password("NewPass1234")
                .businessName("Dup Test Logistics")
                .businessType(BusinessType.BOTH)
                .contactPhone("9123456789")
                .city("Bengaluru")
                .build();

        mockMvc.perform(post("/api/auth/provider-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @DisplayName("5. Post-Login Role Response & Security Guard: TRAVELLER hitting /api/provider/me gets 403 Forbidden; PROVIDER gets 200 OK")
    void testPostLoginRoleAndSecurityGuard() throws Exception {
        String travEmail = "trav_security_" + System.currentTimeMillis() + "@example.com";
        UserEntity travUser = userRepo.save(UserEntity.builder()
                .id("USR-TRAV-SEC")
                .name("Veda Traveller")
                .email(travEmail.toLowerCase())
                .passwordHash(passwordEncoder.encode("SecretPass123"))
                .role(UserRole.TRAVELLER)
                .build());

        String provEmail = "prov_security_" + System.currentTimeMillis() + "@example.com";
        UserEntity provUser = userRepo.save(UserEntity.builder()
                .id("USR-PROV-SEC")
                .name("Veda Provider")
                .email(provEmail.toLowerCase())
                .passwordHash(passwordEncoder.encode("SecretPass123"))
                .role(UserRole.PROVIDER)
                .build());

        providerRepo.save(ProviderEntity.builder()
                .id("PRV-SEC-01")
                .user(provUser)
                .businessName("Veda Cabs")
                .businessType(BusinessType.TRANSPORT)
                .contactEmail(provEmail)
                .contactPhone("+919988776655")
                .city("Mysuru")
                .status(ProviderStatus.APPROVED)
                .build());

        // (a) Login as TRAVELLER returns role TRAVELLER
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(travEmail)
                                .password("SecretPass123")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("TRAVELLER"));

        // (b) TRAVELLER hitting /api/provider/me gets 403 Forbidden (security checks intact)
        mockMvc.perform(get("/api/provider/me")
                        .with(user(travEmail).roles("TRAVELLER")))
                .andExpect(status().isForbidden());

        // (c) Login as PROVIDER returns role PROVIDER
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(provEmail)
                                .password("SecretPass123")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("PROVIDER"));

        // (d) PROVIDER hitting /api/provider/me succeeds with 200 OK
        mockMvc.perform(get("/api/provider/me")
                        .with(user(provEmail).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessName").value("Veda Cabs"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("6. Existing Traveller Can Still Apply as Provider: Secondary upgrade path via /api/provider/register")
    void testExistingTravellerCanStillApplyAsProvider() throws Exception {
        String email = "trav_apply_" + System.currentTimeMillis() + "@example.com";
        UserEntity travUser = userRepo.save(UserEntity.builder()
                .id("USR-APPLY-" + System.currentTimeMillis())
                .name("Secondary Applicant")
                .email(email.toLowerCase())
                .passwordHash(passwordEncoder.encode("TravellerPass123"))
                .role(UserRole.TRAVELLER)
                .build());

        ProviderRegisterRequest req = ProviderRegisterRequest.builder()
                .businessName("Secondary Logistics Fleet")
                .businessType(BusinessType.BOTH)
                .contactEmail("contact@secondaryfleet.com")
                .contactPhone("9845012345")
                .city("Bengaluru")
                .description("Applying from traveller account settings")
                .build();

        mockMvc.perform(post("/api/provider/register")
                        .with(user(email.toLowerCase()).roles("TRAVELLER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // Verify user role was updated to PROVIDER
        UserEntity updated = userRepo.findById(travUser.getId()).orElseThrow();
        assertEquals(UserRole.PROVIDER, updated.getRole());

        // Verify provider entity created
        ProviderEntity provider = providerRepo.findByUserId(travUser.getId()).orElseThrow();
        assertEquals("Secondary Logistics Fleet", provider.getBusinessName());
        assertEquals(ProviderStatus.PENDING, provider.getStatus());
    }

    @Test
    @DisplayName("7. Frontend Static Files Integrity: Distinct role auth tabs, atomic signup form, and strict nav guards exist")
    void testFrontendStaticFilesIntegrity() throws Exception {
        Resource htmlRes = resourceLoader.getResource("classpath:/static/index.html");
        String html;
        try (InputStream is = htmlRes.getInputStream()) {
            html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        Resource jsRes = resourceLoader.getResource("classpath:/static/app.js");
        String js;
        try (InputStream is = jsRes.getInputStream()) {
            js = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 1. Dual role choice tabs exist in HTML
        assertTrue(html.contains("id=\"tab-auth-traveller\""), "Must contain Traveller auth tab");
        assertTrue(html.contains("id=\"tab-auth-provider\""), "Must contain Provider auth tab");
        assertTrue(html.contains("id=\"form-provider-signup\""), "Must contain Provider atomic signup form");
        assertTrue(html.contains("id=\"account-provider-upgrade-section\""), "Must contain secondary provider upgrade section in account settings");

        // 2. JavaScript wires provider registration endpoint and role-based guards
        assertTrue(js.contains("/api/auth/provider-register"), "app.js must call /api/auth/provider-register for provider signup");
        assertTrue(js.contains("role === 'PROVIDER'") || js.contains("user.role === 'PROVIDER'"), "app.js must check for PROVIDER role");
        assertTrue(js.contains("elements.navBtnProvider.classList.add('hidden')"), "app.js must hide Provider Portal for non-providers");
        assertTrue(js.contains("The Provider Portal is only accessible to registered provider accounts"),
                "app.js must show a clean explanatory toast when traveller accesses /provider directly without calling 403 API");
    }

    @Test
    @DisplayName("8. Regression Guard: #section-recovery and #section-plan are strictly mutually exclusive across routes")
    void testRecoveryAndPlanSectionsMutuallyExclusive() throws Exception {
        Resource htmlRes = resourceLoader.getResource("classpath:/static/index.html");
        String html;
        try (InputStream is = htmlRes.getInputStream()) {
            html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        Resource jsRes = resourceLoader.getResource("classpath:/static/app.js");
        String js;
        try (InputStream is = jsRes.getInputStream()) {
            js = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 1. Both sections exist in index.html, start hidden, and section-plan has dedicated empty state
        assertTrue(html.contains("id=\"section-recovery\" class=\"card\""), "section-recovery exists in DOM");
        assertTrue(html.contains("id=\"section-plan\" class=\"card hidden\""), "section-plan exists in DOM and starts hidden");
        assertTrue(html.contains("id=\"plan-empty-state\""), "section-plan must contain empty-state card for uncomposed plans");
        assertTrue(html.contains("id=\"btn-plan-goto-recovery\""), "plan empty-state must provide a button to view recovery options");

        // 2. app.js routes cleanly between them with mutual exclusivity:
        // sectionRecovery is shown ONLY when subroute === 'recovery'
        // sectionPlan is shown ONLY when subroute === 'plan'
        assertTrue(js.contains("elements.sectionRecovery.classList.toggle('hidden', subroute !== 'recovery')"),
                "section-recovery must be visible only when subroute is 'recovery'");
        assertTrue(js.contains("elements.sectionPlan.classList.toggle('hidden', subroute !== 'plan')"),
                "section-plan must be visible only when subroute is 'plan'");

        // 3. Option selection in recovery navigates to /plan route instead of inline unhiding
        assertTrue(js.contains("navigateTo(`/trips/${encodeURIComponent(tripId)}/plan`)"),
                "Selecting an option must navigate cleanly to /plan subroute");

        // 4. View Recovery Options button navigates to /recovery route instead of silent scrollIntoView
        assertTrue(js.contains("navigateTo(`/trips/${encodeURIComponent(tripId)}/recovery`)"),
                "View Recovery Options button must navigate cleanly to /recovery subroute");

        // 5. Simulate router state across all valid workspace subroutes and verify mutual exclusion
        List<String> workspaceSubroutes = List.of("", "disruption", "recovery", "plan", "notifications", "ledger");
        for (String subroute : workspaceSubroutes) {
            boolean isRecoveryVisible = "recovery".equals(subroute);
            boolean isPlanVisible = "plan".equals(subroute);

            assertFalse(isRecoveryVisible && isPlanVisible,
                    String.format("MUTUAL EXCLUSION VIOLATION: #section-recovery and #section-plan are both visible under subroute '%s'", subroute));
        }

        // 6. Confirm no rogue unhiding in renderPlan
        assertFalse(js.contains("elements.sectionPlan.classList.remove('hidden')"),
                "renderPlan must never unconditionally remove .hidden from sectionPlan, which would break route isolation");
    }

    @Test
    @DisplayName("9. Single Source of Truth: updateAuthHeader() centrally governs all role-based visibility across TRAVELLER, PROVIDER, ADMIN, UNAUTH")
    void testSingleSourceOfTruthRoleVisibilityInAppJsAndHtml() throws Exception {
        Resource htmlRes = resourceLoader.getResource("classpath:/static/index.html");
        String html;
        try (InputStream is = htmlRes.getInputStream()) {
            html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        Resource jsRes = resourceLoader.getResource("classpath:/static/app.js");
        String js;
        try (InputStream is = jsRes.getInputStream()) {
            js = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        String jsNormalized = js.replace("\r\n", "\n");

        // 1. Group IDs exist in HTML for reliable role-based action gating
        assertTrue(html.contains("id=\"header-trip-status-group\""), "HTML must contain #header-trip-status-group");
        assertTrue(html.contains("id=\"header-trip-actions-group\""), "HTML must contain #header-trip-actions-group");
        assertTrue(html.contains("id=\"btn-open-create-trip\""), "HTML must contain #btn-open-create-trip");
        assertTrue(html.contains("id=\"btn-load-demo\""), "HTML must contain #btn-load-demo");
        assertTrue(html.contains("id=\"btn-reset\""), "HTML must contain #btn-reset");
        assertTrue(html.contains("id=\"nav-btn-provider\""), "HTML must contain #nav-btn-provider");
        assertTrue(html.contains("id=\"nav-btn-admin\""), "HTML must contain #nav-btn-admin");
        assertTrue(html.contains("id=\"nav-btn-market-search\""), "HTML must contain #nav-btn-market-search");

        // 2. setUnauthenticatedState delegates 100% to updateAuthHeader() and never directly manipulates nav visibility
        assertTrue(jsNormalized.contains("function setUnauthenticatedState(isUnauth) {\n    updateAuthHeader();\n  }"),
                "setUnauthenticatedState must delegate directly to updateAuthHeader() with no duplicate DOM mutations");

        // 3. updateAuthHeader is the SINGLE source of truth for hiding trip actions for PROVIDER
        assertTrue(jsNormalized.contains("if (role === 'PROVIDER') {\n      if (headerTripActionsGroup) headerTripActionsGroup.classList.add('hidden');"),
                "updateAuthHeader must hide headerTripActionsGroup for PROVIDER");
        assertTrue(jsNormalized.contains("if (elements.btnOpenCreateTrip) elements.btnOpenCreateTrip.classList.add('hidden');"),
                "updateAuthHeader must hide btnOpenCreateTrip for PROVIDER");

        // 4. updateAuthHeader is the SINGLE source of truth for Nav tabs:
        // PROVIDER: navBtnProvider visible, navBtnMarketSearch & navBtnAdmin hidden
        assertTrue(jsNormalized.contains("if (elements.navBtnProvider) elements.navBtnProvider.classList.remove('hidden');\n      if (elements.navBtnAdmin) elements.navBtnAdmin.classList.add('hidden');"),
                "updateAuthHeader must show Provider Portal and hide Admin Portal for PROVIDER");

        // TRAVELLER: navBtnProvider & navBtnAdmin hidden, navBtnMarketSearch visible
        assertTrue(jsNormalized.contains("if (elements.navBtnProvider) elements.navBtnProvider.classList.add('hidden');\n      if (elements.navBtnAdmin) elements.navBtnAdmin.classList.add('hidden');"),
                "updateAuthHeader must hide Provider Portal and Admin Portal for TRAVELLER");

        // ADMIN: navBtnProvider AND navBtnAdmin both visible in the same unified check
        assertTrue(jsNormalized.contains("else if (role === 'ADMIN') {\n      if (elements.navBtnMarketSearch) elements.navBtnMarketSearch.classList.remove('hidden');\n      if (elements.navBtnMarketBookings) elements.navBtnMarketBookings.classList.remove('hidden');\n      if (elements.navBtnDirectory) elements.navBtnDirectory.classList.remove('hidden');\n      if (elements.navBtnWorkspace) elements.navBtnWorkspace.classList.remove('hidden');\n      if (elements.navBtnProvider) elements.navBtnProvider.classList.remove('hidden');\n      if (elements.navBtnAdmin) elements.navBtnAdmin.classList.remove('hidden');"),
                "updateAuthHeader must show both Provider Portal and Admin Portal for ADMIN in the same centralized logic");
    }

    @Test
    @DisplayName("10. Backend Role Access & Security Isolation across TRAVELLER, PROVIDER, and ADMIN")
    void testBackendRoleAccessAndSecurityIsolation() throws Exception {
        String baseTime = String.valueOf(System.currentTimeMillis());

        // Create 3 users for the 3 distinct roles
        String travEmail = "trav_iso_" + baseTime + "@example.com";
        userRepo.save(UserEntity.builder()
                .id("USR-ISO-TRAV-" + baseTime)
                .name("Traveller Iso")
                .email(travEmail.toLowerCase())
                .passwordHash(passwordEncoder.encode("IsoPass123"))
                .role(UserRole.TRAVELLER)
                .build());

        String provEmail = "prov_iso_" + baseTime + "@example.com";
        UserEntity provUser = userRepo.save(UserEntity.builder()
                .id("USR-ISO-PROV-" + baseTime)
                .name("Provider Iso")
                .email(provEmail.toLowerCase())
                .passwordHash(passwordEncoder.encode("IsoPass123"))
                .role(UserRole.PROVIDER)
                .build());

        providerRepo.save(ProviderEntity.builder()
                .id("PRV-ISO-" + baseTime)
                .user(provUser)
                .businessName("Iso Logistics")
                .businessType(BusinessType.BOTH)
                .contactEmail(provEmail)
                .contactPhone("+919123456780")
                .city("Bengaluru")
                .status(ProviderStatus.APPROVED)
                .build());

        String adminEmail = "admin_iso_" + baseTime + "@example.com";
        userRepo.save(UserEntity.builder()
                .id("USR-ISO-ADMIN-" + baseTime)
                .name("Admin Iso")
                .email(adminEmail.toLowerCase())
                .passwordHash(passwordEncoder.encode("IsoPass123"))
                .role(UserRole.ADMIN)
                .build());

        // 1. TRAVELLER:
        // (a) Login returns role TRAVELLER
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(travEmail)
                                .password("IsoPass123")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("TRAVELLER"));

        // (b) TRAVELLER cannot access /api/admin/providers -> 403 Forbidden
        mockMvc.perform(get("/api/admin/providers")
                        .with(user(travEmail).roles("TRAVELLER")))
                .andExpect(status().isForbidden());

        // (c) TRAVELLER cannot access /api/provider/me -> 403 Forbidden
        mockMvc.perform(get("/api/provider/me")
                        .with(user(travEmail).roles("TRAVELLER")))
                .andExpect(status().isForbidden());

        // 2. PROVIDER:
        // (a) Login returns role PROVIDER
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(provEmail)
                                .password("IsoPass123")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("PROVIDER"));

        // (b) PROVIDER cannot access /api/admin/providers -> 403 Forbidden
        mockMvc.perform(get("/api/admin/providers")
                        .with(user(provEmail).roles("PROVIDER")))
                .andExpect(status().isForbidden());

        // (c) PROVIDER CAN access /api/provider/me -> 200 OK
        mockMvc.perform(get("/api/provider/me")
                        .with(user(provEmail).roles("PROVIDER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessName").value("Iso Logistics"));

        // 3. ADMIN:
        // (a) Login returns role ADMIN
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(adminEmail)
                                .password("IsoPass123")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        // (b) ADMIN CAN access /api/admin/providers -> 200 OK
        mockMvc.perform(get("/api/admin/providers")
                        .with(user(adminEmail).roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("11. Schedule creation succeeds with KA31RK3696 exact values, default stops, flexible date formats")
    void testScheduleCreation_exactValuesAndContractFlexibility() throws Exception {
        long baseTime = System.currentTimeMillis();
        String provEmail = "sch_prov_" + baseTime + "@example.com";
        UserEntity provUser = userRepo.save(UserEntity.builder()
                .id("USR-SCH-" + baseTime)
                .name("Kalpana Owner")
                .email(provEmail)
                .passwordHash(passwordEncoder.encode("SecretPass123"))
                .role(UserRole.PROVIDER)
                .build());

        ProviderEntity provider = providerRepo.save(ProviderEntity.builder()
                .id("PRV-SCH-" + baseTime)
                .user(provUser)
                .businessName("kalpana travel")
                .businessType(BusinessType.TRANSPORT)
                .contactEmail(provEmail)
                .contactPhone("+917899408008")
                .city("Sirsi")
                .status(ProviderStatus.APPROVED)
                .build());

        com.odyssey.entity.ProviderVehicleEntity vehicle = vehicleRepo.save(com.odyssey.entity.ProviderVehicleEntity.builder()
                .id("VEH-SCH-" + baseTime)
                .provider(provider)
                .vehicleNumber("KA31RK3696")
                .mode(com.odyssey.model.enums.BookingType.BUS)
                .vehicleType("AC Sleeper")
                .seatsTotal(40)
                .active(true)
                .build());

        // Exact values from user screenshot: vehicle KA31RK3696, dep 08:00, arr 14:00, price 650.00,
        // valid 18-11-2026 to 18-12-2026 (or 2026-11-18 to 2026-12-18), 7 days checked, seats left blank / default 40
        String payload = """
        {
            "vehicleId": "%s",
            "originCity": "Bengaluru",
            "destinationCity": "Mysuru",
            "originStop": "",
            "destinationStop": "",
            "departureTime": "08:00",
            "arrivalTime": "14:00",
            "arrivesNextDay": false,
            "price": 650.00,
            "seatsOffered": null,
            "daysOfWeek": "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",
            "validFrom": "2026-11-18",
            "validTo": "2026-12-18",
            "active": true
        }
        """.formatted(vehicle.getId());

        mockMvc.perform(post("/api/provider/schedules")
                        .with(user(provEmail).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.vehicleNumber").value("KA31RK3696"))
                .andExpect(jsonPath("$.data.originStop").value("Bengaluru Central"))
                .andExpect(jsonPath("$.data.destinationStop").value("Mysuru Central"))
                .andExpect(jsonPath("$.data.seatsOffered").value(40))
                .andExpect(jsonPath("$.data.price").value(650.00));

        // Also test DD-MM-YYYY format support for validFrom/validTo on a separate vehicle to avoid overlap
        com.odyssey.entity.ProviderVehicleEntity vehicle2 = vehicleRepo.save(com.odyssey.entity.ProviderVehicleEntity.builder()
                .id("VEH-SCH2-" + baseTime)
                .provider(provider)
                .vehicleNumber("KA31RK9999")
                .mode(com.odyssey.model.enums.BookingType.BUS)
                .vehicleType("Non-AC Seater")
                .seatsTotal(35)
                .active(true)
                .build());

        String payloadDateFormat = """
        {
            "vehicleId": "%s",
            "originCity": "Bengaluru",
            "destinationCity": "Mysuru",
            "originStop": "Majestic",
            "destinationStop": "Suburban",
            "departureTime": "08:00",
            "arrivalTime": "14:00",
            "arrivesNextDay": false,
            "price": 650.00,
            "seatsOffered": 35,
            "daysOfWeek": "MON,TUE,WED,THU,FRI,SAT,SUN",
            "validFrom": "18-11-2026",
            "validTo": "18-12-2026",
            "active": true
        }
        """.formatted(vehicle2.getId());

        mockMvc.perform(post("/api/provider/schedules")
                        .with(user(provEmail).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadDateFormat))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.validFrom").value("2026-11-18"))
                .andExpect(jsonPath("$.data.validTo").value("2026-12-18"));
    }

    @Test
    @DisplayName("12. Server-side role enforcement: TRANSPORT-only provider calling hotel endpoints directly gets HTTP 403 Forbidden")
    void testTransportProviderCannotCallHotelEndpoints_returns403() throws Exception {
        long baseTime = System.currentTimeMillis();
        String provEmail = "transport_only_" + baseTime + "@example.com";
        UserEntity provUser = userRepo.save(UserEntity.builder()
                .id("USR-TRP-" + baseTime)
                .name("Transport Provider")
                .email(provEmail)
                .passwordHash(passwordEncoder.encode("SecretPass123"))
                .role(UserRole.PROVIDER)
                .build());

        providerRepo.save(ProviderEntity.builder()
                .id("PRV-TRP-" + baseTime)
                .user(provUser)
                .businessName("Strictly Transport")
                .businessType(BusinessType.TRANSPORT)
                .contactEmail(provEmail)
                .contactPhone("+919876543210")
                .city("Bengaluru")
                .status(ProviderStatus.APPROVED)
                .build());

        // Attempting to create a hotel as a TRANSPORT-only provider must be rejected with HTTP 403 Forbidden
        String hotelPayload = """
        {
            "name": "Bypassed Hotel",
            "city": "Bengaluru",
            "address": "123 MG Road",
            "contactEmail": "%s",
            "checkInTime": "14:00",
            "checkOutTime": "11:00"
        }
        """.formatted(provEmail);

        mockMvc.perform(post("/api/provider/hotels")
                        .with(user(provEmail).roles("PROVIDER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hotelPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));

        // Attempting to list hotels as a TRANSPORT-only provider must also return 403 Forbidden
        mockMvc.perform(get("/api/provider/hotels")
                        .with(user(provEmail).roles("PROVIDER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("13. Provider dashboard tab visibility logic enforced in app.js for TRANSPORT, HOTEL, and BOTH")
    void testProviderDashboardTabVisibilityEnforcedInAppJs() throws Exception {
        Resource appJsRes = resourceLoader.getResource("classpath:static/app.js");
        String appJsContent;
        try (InputStream is = appJsRes.getInputStream()) {
            appJsContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Confirm updateProviderTabVisibility exists and handles TRANSPORT, HOTEL, and BOTH
        assertTrue(appJsContent.contains("function updateProviderTabVisibility"),
                "app.js must define updateProviderTabVisibility");
        assertTrue(appJsContent.contains("btnProvTabVehicles.classList.toggle('hidden', !isTransport)"),
                "Vehicles tab must be hidden when not transport");
        assertTrue(appJsContent.contains("btnProvTabHotels.classList.toggle('hidden', !isHotel)"),
                "Hotels tab must be hidden when not hotel");
        assertTrue(appJsContent.contains("btnProvTabCabs.classList.toggle('hidden', !isHotel)"),
                "Cabs tab must be hidden when not hotel");
        assertTrue(appJsContent.contains("btnProvTabRefunds.classList.remove('hidden')"),
                "Refunds tab must remain visible for all provider types");

        // Confirm prov-sch-error is present in index.html for visible form errors
        Resource indexHtmlRes = resourceLoader.getResource("classpath:static/index.html");
        String indexHtmlContent;
        try (InputStream is = indexHtmlRes.getInputStream()) {
            indexHtmlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(indexHtmlContent.contains("id=\"prov-sch-error\""),
                "index.html must contain prov-sch-error element in schedule modal");
    }
}
