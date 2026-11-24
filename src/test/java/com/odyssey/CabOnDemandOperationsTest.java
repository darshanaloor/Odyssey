package com.odyssey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.dto.market.CabQuoteResponse;
import com.odyssey.dto.provider.ProviderScheduleRequest;
import com.odyssey.dto.provider.ProviderVehicleDto;
import com.odyssey.dto.provider.ProviderVehicleRequest;
import com.odyssey.entity.CabServiceEntity;
import com.odyssey.entity.ProviderEntity;
import com.odyssey.entity.UserEntity;
import com.odyssey.model.enums.BookingType;
import com.odyssey.model.enums.ProviderStatus;
import com.odyssey.model.enums.UserRole;
import com.odyssey.repository.jpa.CabServiceJpaRepository;
import com.odyssey.repository.jpa.ProviderJpaRepository;
import com.odyssey.repository.jpa.UserJpaRepository;
import com.odyssey.service.MarketplaceService;
import com.odyssey.service.ProviderService;
import com.odyssey.util.IdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class CabOnDemandOperationsTest {

    @Autowired
    private ProviderService providerService;

    @Autowired
    private MarketplaceService marketplaceService;

    @Autowired
    private ProviderJpaRepository providerRepo;

    @Autowired
    private CabServiceJpaRepository cabRepo;

    @Autowired
    private UserJpaRepository userRepo;

    @Test
    @DisplayName("Adding a CAB vehicle auto-syncs on-demand CabService, blocks scheduling, and appears in destination cab list")
    void testCabOnDemandFlow() {
        String email = "cab_owner_" + System.currentTimeMillis() + "@example.com";
        UserEntity user = userRepo.save(UserEntity.builder()
                .id(IdGenerator.generate("USR-"))
                .name("Ravi Verma")
                .email(email)
                .passwordHash("hashed")
                .role(UserRole.PROVIDER)
                .createdAt(LocalDateTime.now())
                .build());

        ProviderEntity provider = providerRepo.save(ProviderEntity.builder()
                .id(IdGenerator.generate("PRV-"))
                .user(user)
                .businessName("Ravi Cab Service")
                .businessType(com.odyssey.model.enums.BusinessType.TRANSPORT)
                .contactEmail(email)
                .city("Solapur")
                .contactPhone("+919876543210")
                .status(ProviderStatus.APPROVED)
                .createdAt(LocalDateTime.now())
                .build());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, java.util.Collections.emptyList())
        );

        // 1. Add CAB vehicle
        ProviderVehicleRequest vehReq = new ProviderVehicleRequest();
        vehReq.setMode(BookingType.CAB);
        String vehNum = "KA 31 A " + (int)(1000 + Math.random() * 9000);
        vehReq.setVehicleNumber(vehNum);
        vehReq.setVehicleType("Sedan (4 Seats)");
        vehReq.setSeatsTotal(4);
        vehReq.setActive(true);

        ProviderVehicleDto savedVeh = providerService.createVehicle(vehReq);
        assertNotNull(savedVeh.getId());
        assertEquals(BookingType.CAB, savedVeh.getMode());

        // Verify CabServiceEntity was auto-synced
        List<CabServiceEntity> providerCabs = cabRepo.findByProviderIdOrderByCreatedAtDesc(provider.getId());
        assertFalse(providerCabs.isEmpty());
        CabServiceEntity matchedCab = providerCabs.stream()
                .filter(c -> vehNum.equalsIgnoreCase(c.getVehicleNumber()))
                .findFirst()
                .orElse(null);
        assertNotNull(matchedCab, "Expected CabServiceEntity to be auto-synced for CAB vehicle");
        assertEquals("Ravi Cab Service", matchedCab.getProvider());
        assertTrue(matchedCab.getCity().equalsIgnoreCase("Solapur") || matchedCab.getCity().equalsIgnoreCase("solapur"));

        // 2. Attempting to schedule this cab vehicle should be blocked
        ProviderScheduleRequest schReq = new ProviderScheduleRequest();
        schReq.setVehicleId(savedVeh.getId());
        schReq.setOriginCity("Solapur");
        schReq.setDestinationCity("Mysuru");
        schReq.setDepartureTime(LocalTime.of(8, 0));
        schReq.setArrivalTime(LocalTime.of(14, 0));
        schReq.setPrice(new BigDecimal("650.00"));
        schReq.setValidFrom(LocalDate.now());
        schReq.setValidTo(LocalDate.now().plusDays(30));
        schReq.setDaysOfWeek("Mon,Tue,Wed,Thu,Fri,Sat,Sun");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            providerService.createSchedule(schReq);
        });
        assertTrue(ex.getMessage().contains("Cabs operate on on-demand availability without fixed timetables"),
                "Expected schedule creation block message, got: " + ex.getMessage());

        // 3. Querying cab quotes for the destination city (Solapur) should include Ravi Cab Service
        List<CabQuoteResponse> quotes = marketplaceService.getCabQuotes("Solapur", "Solapur Central", "Solapur Laxury Resort");
        assertFalse(quotes.isEmpty());
        CabQuoteResponse quote = quotes.stream()
                .filter(q -> q.getProvider() != null && q.getProvider().contains("Ravi Cab Service"))
                .findFirst()
                .orElse(null);
        assertNotNull(quote, "Expected Ravi Cab Service to appear in cab quotes for Solapur");
        assertEquals("PROVIDER", quote.getSource());
        assertTrue(quote.getListingLabel().contains("Ravi Cab Service"));

        // 4. Deleting the vehicle cleans up the cab service
        providerService.deleteVehicle(savedVeh.getId());
        List<CabServiceEntity> remaining = cabRepo.findByProviderIdOrderByCreatedAtDesc(provider.getId());
        boolean stillPresent = remaining.stream().anyMatch(c -> vehNum.equalsIgnoreCase(c.getVehicleNumber()));
        assertFalse(stillPresent, "Expected CabServiceEntity to be deleted when vehicle was deleted");
    }
}
