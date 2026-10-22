package com.odyssey.repository;

import com.odyssey.entity.TripEntity;
import com.odyssey.mapper.TripEntityMapper;
import com.odyssey.model.*;
import com.odyssey.model.enums.*;
import com.odyssey.repository.jpa.TripJpaRepository;
import com.odyssey.util.IdGenerator;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@Transactional
public class TripRepository {

    private static final Logger log = LoggerFactory.getLogger(TripRepository.class);

    private final TripJpaRepository tripJpaRepository;
    private final TripEntityMapper mapper;

    public TripRepository(TripJpaRepository tripJpaRepository, TripEntityMapper mapper) {
        this.tripJpaRepository = tripJpaRepository;
        this.mapper = mapper;
    }

    public Trip save(Trip trip) {
        if (trip.getId() == null || trip.getId().isBlank()) {
            trip.setId(IdGenerator.generate("TRIP"));
        }
        if (trip.getCreatedAt() == null) {
            trip.setCreatedAt(LocalDateTime.now());
        }
        trip.setUpdatedAt(LocalDateTime.now());

        TripEntity entity = mapper.toEntity(trip);
        TripEntity saved = tripJpaRepository.save(entity);
        return mapper.toModel(saved);
    }

    @Transactional(readOnly = true)
    public Optional<Trip> findById(String id) {
        return tripJpaRepository.findById(id).map(mapper::toModel);
    }

    @Transactional(readOnly = true)
    public List<Trip> findAll() {
        return tripJpaRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(mapper::toModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Trip> findVisibleForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return findById("TRIP-DEMO-001").map(List::of).orElse(Collections.emptyList());
        }
        return tripJpaRepository.findVisibleTripsForUser(userId).stream()
                .map(mapper::toModel)
                .collect(Collectors.toList());
    }

    public boolean deleteById(String id) {
        if (tripJpaRepository.existsById(id)) {
            tripJpaRepository.deleteById(id);
            return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public boolean existsById(String id) {
        return tripJpaRepository.existsById(id);
    }

    public void clear() {
        tripJpaRepository.deleteAll();
    }

    public Trip resetDemoTrip() {
        log.info("Resetting demo trip TRIP-DEMO-001 in database...");
        if (tripJpaRepository.existsById("TRIP-DEMO-001")) {
            tripJpaRepository.deleteById("TRIP-DEMO-001");
            tripJpaRepository.flush();
        }
        return createAndSaveDemoTrip();
    }

    @PostConstruct
    public void initSampleData() {
        try {
            if (!tripJpaRepository.existsById("TRIP-DEMO-001")) {
                log.info("Seeding initial TRIP-DEMO-001 demo trip...");
                createAndSaveDemoTrip();
            }
        } catch (Exception e) {
            log.warn("Could not check/seed demo trip on startup (database might still be initializing): {}", e.getMessage());
        }
    }

    private Trip createAndSaveDemoTrip() {
        LocalDateTime tripDate = LocalDateTime.of(2026, 10, 21, 0, 0);

        String bkg1Id = "BKG-101";
        String bkg2Id = "BKG-102";
        String bkg3Id = "BKG-103";
        String bkg4Id = "BKG-104";

        Booking flight = Booking.builder()
                .id(bkg1Id)
                .type(BookingType.FLIGHT)
                .provider("IndiGo 6E-534")
                .from("BLR (Kempegowda International Airport, Bengaluru)")
                .to("IXE (Mangaluru International Airport)")
                .startTime(tripDate.withHour(10).withMinute(30))
                .endTime(tripDate.withHour(11).withMinute(45))
                .cost(new BigDecimal("4500.00"))
                .status(BookingStatus.CONFIRMED)
                .source("INVENTORY")
                .dependsOn(new ArrayList<>())
                .lastStatusState("ON_TIME")
                .lastDelayMinutes(0)
                .lastStatusSource("SIMULATED")
                .lastCheckedAt(LocalDateTime.now())
                .build();

        Booking airportCab = Booking.builder()
                .id(bkg2Id)
                .type(BookingType.CAB)
                .provider("Mangaluru Airport Prepaid Taxi")
                .from("IXE (Mangaluru Airport)")
                .to("The Gateway Hotel, Old Port Road, Mangaluru")
                .startTime(tripDate.withHour(12).withMinute(15))
                .endTime(tripDate.withHour(13).withMinute(0))
                .cost(new BigDecimal("800.00"))
                .status(BookingStatus.CONFIRMED)
                .source("INVENTORY")
                .dependsOn(List.of(bkg1Id))
                .lastStatusState("ON_TIME")
                .lastDelayMinutes(0)
                .lastStatusSource("SIMULATED")
                .lastCheckedAt(LocalDateTime.now())
                .build();

        Booking hotel = Booking.builder()
                .id(bkg3Id)
                .type(BookingType.HOTEL)
                .provider("The Gateway Hotel Mangaluru")
                .from("Old Port Road, Mangaluru")
                .to("Old Port Road, Mangaluru")
                .startTime(tripDate.withHour(13).withMinute(30))
                .endTime(tripDate.plusDays(1).withHour(11).withMinute(0))
                .cost(new BigDecimal("5200.00"))
                .status(BookingStatus.CONFIRMED)
                .source("INVENTORY")
                .dependsOn(List.of(bkg2Id))
                .lastStatusState("ON_TIME")
                .lastDelayMinutes(0)
                .lastStatusSource("SIMULATED")
                .lastCheckedAt(LocalDateTime.now())
                .build();

        Booking familyEvent = Booking.builder()
                .id(bkg4Id)
                .type(BookingType.EVENT)
                .provider("Family Gathering & Dinner Celebration")
                .from("Ocean Pearl Banquet Hall, Kodialbail, Mangaluru")
                .to("Ocean Pearl Banquet Hall, Kodialbail, Mangaluru")
                .startTime(tripDate.withHour(18).withMinute(0))
                .endTime(tripDate.withHour(22).withMinute(0))
                .cost(new BigDecimal("2000.00"))
                .status(BookingStatus.CONFIRMED)
                .source("INVENTORY")
                .dependsOn(List.of(bkg3Id))
                .lastStatusState("ON_TIME")
                .lastDelayMinutes(0)
                .lastStatusSource("SIMULATED")
                .lastCheckedAt(LocalDateTime.now())
                .build();

        Constraints constraints = Constraints.builder()
                .maxBudget(new BigDecimal("15000.00"))
                .mustArriveBy(tripDate.withHour(17).withMinute(30))
                .preferredModes(List.of(BookingType.FLIGHT, BookingType.CAB))
                .avoidOvernightTravel(true)
                .hardCommitments(List.of("Family Event at 6:00 PM in Mangaluru"))
                .build();

        AuditEntry initialAudit = AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.SYSTEM)
                .action("TRIP_INITIALIZED")
                .details("Bengaluru to Mangaluru trip initialized with flight (BLR-IXE), cab, hotel check-in, and 6:00 PM family event.")
                .build();

        Trip sampleTrip = Trip.builder()
                .id("TRIP-DEMO-001")
                .title("Bengaluru to Mangaluru Family Gathering")
                .description("Family trip: Morning flight from BLR to IXE, airport cab, hotel check-in, and 6:00 PM family event in Mangaluru.")
                .travellerName("Ananya Rao")
                .travellerPhone("+91-9876543210")
                .travellerEmail("ananya.rao@example.com")
                .needsDestinationCab(true)
                .destinationCabDropoff("The Gateway Hotel, Old Port Road, Mangaluru")
                .needsHotel(true)
                .status("CONFIRMED")
                .bookings(new ArrayList<>(List.of(flight, airportCab, hotel, familyEvent)))
                .constraints(constraints)
                .activeDisruptions(new ArrayList<>())
                .options(new ArrayList<>())
                .auditLog(new ArrayList<>(List.of(initialAudit)))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return save(sampleTrip);
    }
}
