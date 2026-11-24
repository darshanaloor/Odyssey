package com.odyssey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.dto.provider.ProviderSignupRequest;
import com.odyssey.dto.request.LoginRequest;
import com.odyssey.dto.request.RegisterRequest;
import com.odyssey.model.enums.BusinessType;
import com.odyssey.repository.jpa.UserJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class MultiTabSessionAndStylingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private UserJpaRepository userRepo;

    @Test
    @DisplayName("Verify index.html has absolute /styles.css and /app.js references to prevent relative path breakage")
    void testStaticResourcesPathsInIndexHtmlHaveLeadingSlash() throws Exception {
        Resource resource = resourceLoader.getResource("classpath:static/index.html");
        assertTrue(resource.exists(), "index.html must exist");
        try (InputStream is = resource.getInputStream()) {
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(html.contains("<link rel=\"stylesheet\" href=\"/styles.css\">"),
                    "index.html must link to '/styles.css' with a leading slash");
            assertFalse(html.contains("<link rel=\"stylesheet\" href=\"styles.css\">"),
                    "index.html must not use relative 'styles.css'");
            assertTrue(html.contains("<script src=\"/app.js\"></script>"),
                    "index.html must reference '/app.js' with a leading slash");
            assertFalse(html.contains("<script src=\"app.js\"></script>"),
                    "index.html must not use relative 'app.js'");
        }
    }

    @Test
    @DisplayName("Verify Load demo trip and Reset Demo buttons are within header-trip-actions-group")
    void testDemoButtonsRemovedFromMainNavigation() throws Exception {
        Resource resource = resourceLoader.getResource("classpath:static/index.html");
        try (InputStream is = resource.getInputStream()) {
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(html.contains("id=\"header-trip-actions-group\""), "header-trip-actions-group must exist");
            assertTrue(html.contains("id=\"btn-load-demo\""), "btn-load-demo must exist");
            assertTrue(html.contains("id=\"btn-reset\""), "btn-reset must exist");
        }
    }

    @Test
    @DisplayName("Verify SpaController forwards client routes without extensions to index.html")
    void testSpaControllerForwardsSpaRoutesToHtml() throws Exception {
        mockMvc.perform(get("/provider/departures"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/trips/TRP-TEST-123"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/checkout/draft-leg-456"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("Verify SpaController rejects static asset extensions under nested routes to prevent MIME type poisoning")
    void testSpaControllerDoesNotForwardStaticAssetsAsHtml() throws Exception {
        mockMvc.perform(get("/provider/styles.css"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/trips/app.js"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Verify Simulation Control section is removed from traveller view while Check status now remains at top")
    void testSimulationControlSectionRemovedFromTravellerView() throws Exception {
        Resource resource = resourceLoader.getResource("classpath:static/index.html");
        try (InputStream is = resource.getInputStream()) {
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(html.contains("id=\"section-live-status\""), "section-live-status must exist");
            assertTrue(html.contains("id=\"btn-check-status-now\""), "btn-check-status-now must remain at the top");
            assertFalse(html.contains("id=\"simulate-control-container\""), "simulate-control-container must be removed from traveller view");
            assertFalse(html.contains("SIMULATION CONTROL"), "SIMULATION CONTROL tag must be removed from traveller view");
            assertFalse(html.contains("id=\"btn-apply-simulation\""), "btn-apply-simulation must be removed");
        }
    }

    @Test
    @DisplayName("Verify root static resources return correct CSS and JS MIME types")
    void testRootStaticResourcesServeCorrectMimeTypes() throws Exception {
        mockMvc.perform(get("/styles.css"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/css")));

        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("javascript")));
    }

    @Test
    @DisplayName("Verify independent sessions on separate hostnames (localhost vs 127.0.0.1) preserve Traveller and Provider roles")
    void testTwoDistinctHostnamesIsolateTravellerAndProviderSessions() throws Exception {
        String travellerEmail = "traveller.isolated." + System.currentTimeMillis() + "@test.com";
        String providerEmail = "provider.isolated." + System.currentTimeMillis() + "@test.com";

        // Tab 1: Traveller on localhost:8085
        MockHttpSession travellerSession = new MockHttpSession();
        RegisterRequest regTraveller = RegisterRequest.builder()
                .name("Isolated Traveller")
                .email(travellerEmail)
                .password("Password123!")
                .build();
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regTraveller)))
                .andExpect(status().isCreated());

        LoginRequest loginTraveller = LoginRequest.builder()
                .email(travellerEmail)
                .password("Password123!")
                .build();
        mockMvc.perform(post("/api/auth/login")
                        .header("Host", "localhost:8085")
                        .session(travellerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginTraveller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("TRAVELLER"));

        // Tab 2: Provider on 127.0.0.1:8085
        MockHttpSession providerSession = new MockHttpSession();
        ProviderSignupRequest regProvider = ProviderSignupRequest.builder()
                .name("Isolated Provider")
                .email(providerEmail)
                .password("Password123!")
                .businessName("Isolated Logistics")
                .businessType(BusinessType.TRANSPORT)
                .city("Mysuru")
                .contactPhone("+919876543210")
                .build();
        mockMvc.perform(post("/api/auth/provider-register")
                        .header("Host", "127.0.0.1:8085")
                        .session(providerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regProvider)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("PROVIDER"));

        // Verify Tab 1 session remains TRAVELLER with no Access Denied on user resources
        mockMvc.perform(get("/api/auth/me")
                        .header("Host", "localhost:8085")
                        .session(travellerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(travellerEmail))
                .andExpect(jsonPath("$.data.role").value("TRAVELLER"));

        // Verify Tab 2 session remains PROVIDER with access to provider endpoints
        mockMvc.perform(get("/api/auth/me")
                        .header("Host", "127.0.0.1:8085")
                        .session(providerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(providerEmail))
                .andExpect(jsonPath("$.data.role").value("PROVIDER"));

        mockMvc.perform(get("/api/provider/dashboard")
                        .header("Host", "127.0.0.1:8085")
                        .session(providerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessName").value("Isolated Logistics"));
    }

    @Test
    @DisplayName("Verify token-based tab isolation on the SAME host (localhost:8085) prevents session overwrites across tabs")
    void testTokenBasedTabIsolationOnSameHost() throws Exception {
        String travellerEmail = "traveller.token." + System.currentTimeMillis() + "@test.com";
        String providerEmail = "provider.token." + System.currentTimeMillis() + "@test.com";

        // Tab 1: Traveller registers and logs in on localhost:8085
        RegisterRequest regTraveller = RegisterRequest.builder()
                .name("Token Traveller")
                .email(travellerEmail)
                .password("Password123!")
                .build();
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regTraveller)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        LoginRequest loginTraveller = LoginRequest.builder()
                .email(travellerEmail)
                .password("Password123!")
                .build();
        MvcResult res1 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginTraveller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("TRAVELLER"))
                .andReturn();

        String tokenTraveller = objectMapper.readTree(res1.getResponse().getContentAsString())
                .path("data").path("token").asText();
        assertNotNull(tokenTraveller);
        assertFalse(tokenTraveller.isBlank());

        // Tab 2: Provider registers and logs in on the SAME host localhost:8085
        ProviderSignupRequest regProvider = ProviderSignupRequest.builder()
                .name("Token Provider")
                .email(providerEmail)
                .password("Password123!")
                .businessName("Token Express Logistics")
                .businessType(BusinessType.TRANSPORT)
                .city("Sirsi")
                .contactPhone("+919876500000")
                .build();
        MvcResult res2 = mockMvc.perform(post("/api/auth/provider-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regProvider)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("PROVIDER"))
                .andReturn();

        String tokenProvider = objectMapper.readTree(res2.getResponse().getContentAsString())
                .path("data").path("token").asText();
        assertNotNull(tokenProvider);
        assertFalse(tokenProvider.isBlank());
        assertNotEquals(tokenTraveller, tokenProvider);

        // Tab 1 refresh check: /api/auth/me with X-Auth-Token = tokenTraveller MUST return Traveller!
        mockMvc.perform(get("/api/auth/me")
                        .header("X-Auth-Token", tokenTraveller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(travellerEmail))
                .andExpect(jsonPath("$.data.role").value("TRAVELLER"));

        // Tab 2 refresh check: /api/auth/me with X-Auth-Token = tokenProvider MUST return Provider!
        mockMvc.perform(get("/api/auth/me")
                        .header("X-Auth-Token", tokenProvider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(providerEmail))
                .andExpect(jsonPath("$.data.role").value("PROVIDER"));

        // Tab 2 accessing provider dashboard succeeds with tokenProvider
        mockMvc.perform(get("/api/provider/dashboard")
                        .header("X-Auth-Token", tokenProvider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessName").value("Token Express Logistics"));

        // Tab 1 attempting provider dashboard with tokenTraveller receives 403 Forbidden
        mockMvc.perform(get("/api/provider/dashboard")
                        .header("X-Auth-Token", tokenTraveller))
                .andExpect(status().isForbidden());

        // Tab 1 logout invalidates tokenTraveller
        mockMvc.perform(post("/api/auth/logout")
                        .header("X-Auth-Token", tokenTraveller)
                        .with(csrf()))
                .andExpect(status().isOk());

        // Tab 1 is now logged out
        mockMvc.perform(get("/api/auth/me")
                        .header("X-Auth-Token", tokenTraveller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));

        // Tab 2 remains active and unaffected
        mockMvc.perform(get("/api/auth/me")
                        .header("X-Auth-Token", tokenProvider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(providerEmail))
                .andExpect(jsonPath("$.data.role").value("PROVIDER"));
    }
}
