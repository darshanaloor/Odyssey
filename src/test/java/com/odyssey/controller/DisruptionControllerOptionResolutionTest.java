package com.odyssey.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.dto.request.ReportDisruptionRequest;
import com.odyssey.dto.request.SelectOptionRequest;
import com.odyssey.model.enums.DisruptionType;
import com.odyssey.entity.UserEntity;
import com.odyssey.model.Trip;
import com.odyssey.repository.TripRepository;
import com.odyssey.repository.jpa.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class DisruptionControllerOptionResolutionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private UserEntity testUser;
    private String tripId;
    private String option1Id;
    private String option2Id;

    @BeforeEach
    void setUp() throws Exception {
        testUser = userJpaRepository.findByEmail("disruption_test@example.com").orElseGet(() -> {
            return userJpaRepository.save(UserEntity.builder()
                    .id("USR-DISRUPT")
                    .email("disruption_test@example.com")
                    .passwordHash(passwordEncoder.encode("Password123"))
                    .name("Disruption Test User")
                    .createdAt(LocalDateTime.now())
                    .build());
        });

        Trip demo = tripRepository.resetDemoTrip();
        Trip customTrip = tripRepository.findById(demo.getId()).orElseThrow();
        customTrip.setUserId(testUser.getId());
        tripRepository.save(customTrip);
        tripId = customTrip.getId();

        // Report disruption on flight BKG-101
        ReportDisruptionRequest disruptionReq = ReportDisruptionRequest.builder()
                .type(DisruptionType.CANCELLATION)
                .bookingId("BKG-101")
                .reason("Engine fault")
                .build();

        mockMvc.perform(post("/api/trips/" + tripId + "/disruptions")
                        .with(user(testUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disruptionReq)))
                .andExpect(status().isOk());

        // Generate recovery options
        MvcResult recoverResult = mockMvc.perform(post("/api/trips/" + tripId + "/recover")
                        .with(user(testUser.getEmail())).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = recoverResult.getResponse().getContentAsString();
        JsonNode rootNode = objectMapper.readTree(responseJson);
        JsonNode optionsNode = rootNode.path("data").path("options");

        option1Id = optionsNode.get(0).path("id").asText();
        if (optionsNode.size() > 1) {
            option2Id = optionsNode.get(1).path("id").asText();
        } else {
            option2Id = option1Id;
        }
    }

    @Test
    @DisplayName("POST /plan with body only resolves optionId from body")
    void testBuildPlan_bodyOnly() throws Exception {
        SelectOptionRequest body = SelectOptionRequest.builder().optionId(option1Id).build();

        mockMvc.perform(post("/api/trips/" + tripId + "/plan")
                        .with(user(testUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.chosenOptionId", is(option1Id)));
    }

    @Test
    @DisplayName("POST /plan with query only resolves optionId from query parameter")
    void testBuildPlan_queryOnly() throws Exception {
        mockMvc.perform(post("/api/trips/" + tripId + "/plan")
                        .with(user(testUser.getEmail())).with(csrf())
                        .param("optionId", option1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.chosenOptionId", is(option1Id)));
    }

    @Test
    @DisplayName("POST /plan with both body and query parameter resolves optionId from body (body wins)")
    void testBuildPlan_bothBodyAndQuery() throws Exception {
        SelectOptionRequest body = SelectOptionRequest.builder().optionId(option1Id).build();

        mockMvc.perform(post("/api/trips/" + tripId + "/plan")
                        .with(user(testUser.getEmail())).with(csrf())
                        .param("optionId", option2Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.chosenOptionId", is(option1Id))); // Body wins!
    }

    @Test
    @DisplayName("POST /plan with neither body nor query parameter returns 400 optionId is required")
    void testBuildPlan_neither_returns400() throws Exception {
        mockMvc.perform(post("/api/trips/" + tripId + "/plan")
                        .with(user(testUser.getEmail())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("optionId is required")));
    }

    @Test
    @DisplayName("POST /approve and /reject also support body or query parameter and 400 when missing")
    void testApproveAndReject_resolution() throws Exception {
        // Build plan first
        mockMvc.perform(post("/api/trips/" + tripId + "/plan")
                        .with(user(testUser.getEmail())).with(csrf())
                        .param("optionId", option1Id))
                .andExpect(status().isOk());

        // Approve without optionId returns 400
        mockMvc.perform(post("/api/trips/" + tripId + "/approve")
                        .with(user(testUser.getEmail())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("optionId is required")));

        // Reject without optionId returns 400
        mockMvc.perform(post("/api/trips/" + tripId + "/reject")
                        .with(user(testUser.getEmail())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("optionId is required")));

        // Approve with body only succeeds
        SelectOptionRequest body = SelectOptionRequest.builder().optionId(option1Id).build();
        mockMvc.perform(post("/api/trips/" + tripId + "/approve")
                        .with(user(testUser.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.chosenOptionId", is(option1Id)));
    }
}
