package com.odyssey;

import com.odyssey.dto.market.BookingVerificationRequest;
import com.odyssey.dto.market.BookingVerificationResponse;
import com.odyssey.dto.market.MarketplaceBookingDto;
import com.odyssey.entity.BookingEntity;
import com.odyssey.entity.CabServiceEntity;
import com.odyssey.entity.ProviderEntity;
import com.odyssey.entity.TripEntity;
import com.odyssey.entity.UserEntity;
import com.odyssey.model.enums.BusinessType;
import com.odyssey.model.enums.ProviderStatus;
import com.odyssey.model.enums.UserRole;
import com.odyssey.model.enums.VehicleCategory;
import com.odyssey.repository.jpa.BookingJpaRepository;
import com.odyssey.repository.jpa.CabServiceJpaRepository;
import com.odyssey.repository.jpa.ProviderJpaRepository;
import com.odyssey.repository.jpa.TripJpaRepository;
import com.odyssey.repository.jpa.UserJpaRepository;
import com.odyssey.service.MarketplaceService;
import com.odyssey.service.ProviderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CabLateArrivalVerificationTest {

    @Autowired
    private MarketplaceService marketplaceService;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private UserJpaRepository userRepo;

    @Autowired
    private ProviderJpaRepository providerRepo;

    @Autowired
    private CabServiceJpaRepository cabRepo;

    @Autowired
    private TripJpaRepository tripRepo;

    @Autowired
    private BookingJpaRepository bookingRepo;

    @Test
    void recoveryCabLateArrivalVerification_andFallbackToNewCab_worksEndToEnd() {
        long ts = System.currentTimeMillis();

        UserEntity traveller = userRepo.save(UserEntity.builder()
                .id("USR-TRV-" + ts)
                .name("Darshan")
                .email("darshan." + ts + "@test.com")
                .passwordHash("hashed")
                .role(UserRole.TRAVELLER)
                .build());

        UserEntity oldCabUser = userRepo.save(UserEntity.builder()
                .id("USR-CAB1-" + ts)
                .name("Old Cab Owner")
                .email("oldcab." + ts + "@test.com")
                .passwordHash("hashed")
                .role(UserRole.PROVIDER)
                .build());

        ProviderEntity oldCabProvider = providerRepo.save(ProviderEntity.builder()
                .id("PRV-CAB1-" + ts)
                .user(oldCabUser)
                .businessName("Ravi Cab Service " + ts)
                .businessType(BusinessType.TRANSPORT)
                .city("Solapur")
                .contactEmail("ravi." + ts + "@test.com")
                .contactPhone("+919900001111")
                .status(ProviderStatus.APPROVED)
                .createdAt(LocalDateTime.now())
                .build());

        CabServiceEntity oldCabService = cabRepo.save(CabServiceEntity.builder()
                .id("CAB-SVC1-" + ts)
                .providerId(oldCabProvider.getId())
                .provider(oldCabProvider.getBusinessName())
                .city("Solapur")
                .vehicleCategory(VehicleCategory.SEDAN)
                .baseFare(BigDecimal.valueOf(150))
                .perKm(BigDecimal.valueOf(18))
                .seats(4)
                .source("PROVIDER")
                .build());

        UserEntity newCabUser = userRepo.save(UserEntity.builder()
                .id("USR-CAB2-" + ts)
                .name("New Cab Owner")
                .email("newcab." + ts + "@test.com")
                .passwordHash("hashed")
                .role(UserRole.PROVIDER)
                .build());

        ProviderEntity newCabProvider = providerRepo.save(ProviderEntity.builder()
                .id("PRV-CAB2-" + ts)
                .user(newCabUser)
                .businessName("Solapur Swift Cabs " + ts)
                .businessType(BusinessType.TRANSPORT)
                .city("Solapur")
                .contactEmail("swift." + ts + "@test.com")
                .contactPhone("+919900002222")
                .status(ProviderStatus.APPROVED)
                .createdAt(LocalDateTime.now())
                .build());

        CabServiceEntity newCabService = cabRepo.save(CabServiceEntity.builder()
                .id("CAB-SVC2-" + ts)
                .providerId(newCabProvider.getId())
                .provider(newCabProvider.getBusinessName())
                .city("Solapur")
                .vehicleCategory(VehicleCategory.SUV)
                .baseFare(BigDecimal.valueOf(200))
                .perKm(BigDecimal.valueOf(20))
                .seats(6)
                .source("PROVIDER")
                .build());

        TripEntity trip = tripRepo.save(TripEntity.builder()
                .id("TRP-" + ts)
                .title("Bangalore to Solapur")
                .travellerName("Darshan")
                .userId(traveller.getId())
                .status("DISRUPTED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        BookingEntity existingCabBooking = bookingRepo.save(BookingEntity.builder()
                .id("BKG-OLD-CAB-" + ts)
                .trip(trip)
                .user(traveller)
                .type("CAB")
                .provider(oldCabProvider.getBusinessName())
                .providerId(oldCabProvider.getId())
                .serviceId(oldCabService.getId())
                .fromLocation("Solapur")
                .toLocation("Hotel Sunrise")
                .startTime(LocalDateTime.now().plusHours(4))
                .endTime(LocalDateTime.now().plusHours(5))
                .cost(BigDecimal.valueOf(350))
                .status("CONFIRMED")
                .cannotHonour(false)
                .build());

        LocalDateTime newPickupTime = LocalDateTime.now().plusHours(7).withNano(0);
        String lateMessage = "Traveller is coming late due to transit disruption. Calculated arrival: 18:00";

        // 1. Traveller sends late-arrival verification to existing cab provider
        BookingVerificationResponse oldCabVerify = marketplaceService.requestBookingVerification(
                BookingVerificationRequest.builder()
                        .type("CAB")
                        .existingCabBookingId(existingCabBooking.getId())
                        .tripId(trip.getId())
                        .city("Solapur")
                        .pickup("Solapur")
                        .dropoff("Hotel Sunrise")
                        .pickupTime(newPickupTime)
                        .verificationNote(lateMessage)
                        .build(),
                traveller);

        assertTrue(oldCabVerify.isSuccess());
        assertEquals("PENDING_PROVIDER_CONFIRMATION", oldCabVerify.getStatus());

        // 2. Old cab provider sees the late-arrival message and calculated pickup time in pending requests
        setAuth(oldCabUser);
        List<MarketplaceBookingDto> oldProviderPending = providerService.getPendingBookings();
        MarketplaceBookingDto pendingForOldCab = oldProviderPending.stream()
                .filter(b -> b.getId().equals(oldCabVerify.getBookingId()))
                .findFirst()
                .orElse(null);
        assertNotNull(pendingForOldCab);
        assertEquals(lateMessage, pendingForOldCab.getVerificationNote());
        assertEquals(newPickupTime, pendingForOldCab.getStartTime());

        // 3. Old cab provider rejects the late-arrival request
        MarketplaceBookingDto rejected = providerService.rejectBooking(oldCabVerify.getBookingId(), "Driver unavailable at late hour");
        assertEquals("CANCELLED", rejected.getStatus());
        assertEquals("REJECTED", rejected.getProviderConfirmationStatus());

        // 4. Traveller selects a new cab and sends verification to the new cab driver
        setAuth(traveller);
        BookingVerificationResponse newCabVerify = marketplaceService.requestBookingVerification(
                BookingVerificationRequest.builder()
                        .type("CAB")
                        .targetId(newCabService.getId())
                        .targetName(newCabProvider.getBusinessName())
                        .tripId(trip.getId())
                        .city("Solapur")
                        .pickup("Solapur")
                        .dropoff("Hotel Sunrise")
                        .pickupTime(newPickupTime)
                        .verificationNote(lateMessage)
                        .build(),
                traveller);

        assertTrue(newCabVerify.isSuccess());
        assertEquals("PENDING_PROVIDER_CONFIRMATION", newCabVerify.getStatus());

        // 5. New cab provider sees the request with late-arrival note and accepts it
        setAuth(newCabUser);
        List<MarketplaceBookingDto> newProviderPending = providerService.getPendingBookings();
        MarketplaceBookingDto pendingForNewCab = newProviderPending.stream()
                .filter(b -> b.getId().equals(newCabVerify.getBookingId()))
                .findFirst()
                .orElse(null);
        assertNotNull(pendingForNewCab);
        assertEquals(lateMessage, pendingForNewCab.getVerificationNote());

        MarketplaceBookingDto accepted = providerService.acceptBooking(newCabVerify.getBookingId(), "Ramesh Driver");
        assertEquals("CONFIRMED", accepted.getStatus());
        assertEquals("ACCEPTED", accepted.getProviderConfirmationStatus());
        assertEquals("Ramesh Driver", accepted.getAssignedDriverName());

        SecurityContextHolder.clearContext();
    }

    private void setAuth(UserEntity user) {
        String role = "ROLE_" + user.getRole().name();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        null,
                        List.of(new SimpleGrantedAuthority(role))
                )
        );
    }
}
