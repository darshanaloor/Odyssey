package com.odyssey.service;

import com.odyssey.config.ProviderPortalProperties;
import com.odyssey.dto.market.MarketplaceBookingDto;
import com.odyssey.dto.provider.*;
import com.odyssey.entity.*;
import com.odyssey.exception.ConflictException;
import com.odyssey.exception.RateLimitException;
import com.odyssey.exception.ResourceNotFoundException;
import com.odyssey.model.CityInfo;
import com.odyssey.model.enums.*;
import com.odyssey.repository.jpa.*;
import com.odyssey.service.status.StatusCheckService;
import com.odyssey.util.EmergencyContactUtil;
import com.odyssey.util.IdGenerator;
import com.odyssey.util.NotificationMasker;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderService {

    private final ProviderJpaRepository providerRepo;
    private final ProviderVehicleJpaRepository vehicleRepo;
    private final ProviderScheduleJpaRepository scheduleRepo;
    private final ServiceJpaRepository serviceRepo;
    private final ServiceDepartureJpaRepository departureRepo;
    private final OperatorJpaRepository operatorRepo;
    private final BookingJpaRepository bookingRepo;
    private final UserJpaRepository userRepo;
    private final AuditEntryJpaRepository auditRepo;
    private final AuthService authService;
    private final ProviderPortalProperties props;
    private final HotelJpaRepository hotelRepo;
    private final HotelRoomTypeJpaRepository roomTypeRepo;
    private final HotelRoomAvailabilityJpaRepository availabilityRepo;
    private final CabServiceJpaRepository cabRepo;
    private final TripJpaRepository tripRepo;
    private final DisruptionJpaRepository disruptionRepo;
    private final TripLedgerEntryJpaRepository ledgerRepo;
    private final NotificationJpaRepository notificationRepo;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private StatusCheckService statusCheckService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NotificationService notificationService;

    // In-memory sliding window rate limiter: 60 write calls per minute per provider/user
    private final ConcurrentMap<String, List<Instant>> writeRateLimits = new ConcurrentHashMap<>();
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    // =========================================================================
    // 1. Onboarding & Registration
    // =========================================================================

    @Transactional
    public ProviderResponse registerProvider(ProviderRegisterRequest request, HttpServletRequest httpRequest) {
        UserEntity currentUser = authService.getCurrentUserEntity();
        if (currentUser == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required to register as a provider");
        }

        checkWriteRateLimit(currentUser.getId());

        if (providerRepo.existsByUserId(currentUser.getId())) {
            throw new IllegalArgumentException("User already has a provider profile");
        }

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

        // Normalise contact phone (must be 10-digit Indian number)
        String normalizedPhone = EmergencyContactUtil.normalizePhone(request.getContactPhone());

        String providerId = IdGenerator.generate("PRV");
        ProviderEntity provider = ProviderEntity.builder()
                .id(providerId)
                .user(currentUser)
                .businessName(request.getBusinessName().trim())
                .businessType(request.getBusinessType())
                .contactEmail(request.getContactEmail().trim().toLowerCase())
                .contactPhone(normalizedPhone)
                .city(cityInfo.getName())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .status(ProviderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ProviderEntity saved = providerRepo.save(provider);

        // Update user entity role to PROVIDER
        currentUser.setRole(UserRole.PROVIDER);
        userRepo.save(currentUser);

        // Refresh authentication context so the user gets ROLE_PROVIDER in the active session
        authService.refreshAuthenticationRole(currentUser, httpRequest);

        // Audit entry
        recordAudit(AuditActor.USER, "PROVIDER_REGISTERED",
                "User registered as provider: " + saved.getBusinessName() + " (ID: " + saved.getId() + ")");

        log.info("Registered provider: id={}, businessName={}, email={}",
                saved.getId(), saved.getBusinessName(), NotificationMasker.maskEmail(saved.getContactEmail()));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProviderResponse getCurrentProviderProfile() {
        ProviderEntity provider = requireCurrentProvider();
        return toResponse(provider);
    }

    // =========================================================================
    // 2. Transport Vehicles CRUD
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ProviderVehicleDto> getVehicles() {
        ProviderEntity provider = requireCurrentProvider();
        List<ProviderVehicleEntity> vehicles = vehicleRepo.findByProviderIdOrderByCreatedAtDesc(provider.getId());

        return vehicles.stream().map(v -> {
            int maxBooked = getMaxBookedSeatsInFuture(v.getId());
            return toVehicleDto(v, maxBooked);
        }).collect(Collectors.toList());
    }

    @Transactional
    public ProviderVehicleDto createVehicle(ProviderVehicleRequest req) {
        ProviderEntity provider = requireCurrentProvider();
        checkWriteRateLimit(provider.getId());

        long count = vehicleRepo.countByProviderId(provider.getId());
        if (count >= props.getMaxVehicles()) {
            throw new ConflictException("Vehicle limit reached (maximum " + props.getMaxVehicles() + " vehicles per provider)");
        }

        String vehNum = req.getVehicleNumber().trim().toUpperCase();
        if (vehicleRepo.existsByProviderIdAndVehicleNumberIgnoreCase(provider.getId(), vehNum)) {
            throw new IllegalArgumentException("Vehicle number '" + vehNum + "' already exists for this provider");
        }

        ProviderVehicleEntity vehicle = ProviderVehicleEntity.builder()
                .id(IdGenerator.generate("VEH"))
                .provider(provider)
                .mode(req.getMode())
                .vehicleNumber(vehNum)
                .vehicleType(req.getVehicleType() != null && !req.getVehicleType().isBlank() ? req.getVehicleType().trim() : "Standard")
                .seatsTotal(req.getSeatsTotal())
                .amenities(req.getAmenities() != null ? req.getAmenities().trim() : null)
                .active(req.getActive() != null ? req.getActive() : true)
                .createdAt(LocalDateTime.now())
                .build();

        ProviderVehicleEntity saved = vehicleRepo.save(vehicle);
        log.info("Created vehicle: id={}, providerId={}, vehicleNumber={}", saved.getId(), provider.getId(), vehNum);
        return toVehicleDto(saved, 0);
    }

    @Transactional
    public ProviderVehicleDto updateVehicle(String vehicleId, ProviderVehicleRequest req) {
        ProviderEntity provider = requireCurrentProvider();
        checkWriteRateLimit(provider.getId());

        ProviderVehicleEntity vehicle = vehicleRepo.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with ID: " + vehicleId));
        if (!vehicle.getProvider().getId().equals(provider.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("You do not own this vehicle");
        }

        String vehNum = req.getVehicleNumber().trim().toUpperCase();
        if (vehicleRepo.existsByProviderIdAndVehicleNumberIgnoreCaseAndIdNot(provider.getId(), vehNum, vehicleId)) {
            throw new IllegalArgumentException("Vehicle number '" + vehNum + "' already exists for another vehicle of this provider");
        }

        // Check if seatsTotal is being reduced below maximum booked seats on future departures
        int maxBooked = getMaxBookedSeatsInFuture(vehicleId);
        if (req.getSeatsTotal() < maxBooked) {
            throw new ConflictException("Cannot reduce total seats to " + req.getSeatsTotal() +
                    " because future departures already have up to " + maxBooked + " seats booked");
        }

        vehicle.setMode(req.getMode());
        vehicle.setVehicleNumber(vehNum);
        vehicle.setVehicleType(req.getVehicleType() != null && !req.getVehicleType().isBlank() ? req.getVehicleType().trim() : (vehicle.getVehicleType() != null ? vehicle.getVehicleType() : "Standard"));
        vehicle.setSeatsTotal(req.getSeatsTotal());
        vehicle.setAmenities(req.getAmenities() != null ? req.getAmenities().trim() : null);
        if (req.getActive() != null) {
            vehicle.setActive(req.getActive());
        }

        ProviderVehicleEntity saved = vehicleRepo.save(vehicle);
        log.info("Updated vehicle: id={}, vehicleNumber={}", saved.getId(), vehNum);
        return toVehicleDto(saved, maxBooked);
    }

    @Transactional
    public void deleteVehicle(String vehicleId) {
        ProviderEntity provider = requireCurrentProvider();
        checkWriteRateLimit(provider.getId());

        ProviderVehicleEntity vehicle = vehicleRepo.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with ID: " + vehicleId));
        if (!vehicle.getProvider().getId().equals(provider.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("You do not own this vehicle");
        }

        int maxBooked = getMaxBookedSeatsInFuture(vehicleId);
        if (maxBooked > 0) {
            throw new ConflictException("Cannot delete vehicle with active future bookings (" + maxBooked +
                    " seats booked). You may deactivate it instead.");
        }

        vehicleRepo.delete(vehicle);
        log.info("Deleted vehicle: id={}, providerId={}", vehicleId, provider.getId());
    }

    // =========================================================================
    // 3. Schedules CRUD & Departure Generation
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ProviderScheduleDto> getSchedules() {
        ProviderEntity provider = requireCurrentProvider();
        List<ProviderScheduleEntity> schedules = scheduleRepo.findByProviderIdOrderByCreatedAtDesc(provider.getId());
        return schedules.stream().map(this::toScheduleDto).collect(Collectors.toList());
    }

    @Transactional
    public ProviderScheduleDto createSchedule(ProviderScheduleRequest req) {
        ProviderEntity provider = requireCurrentProvider();
        checkWriteRateLimit(provider.getId());

        long count = scheduleRepo.countByProviderId(provider.getId());
        if (count >= props.getMaxSchedules()) {
            throw new ConflictException("Schedule limit reached (maximum " + props.getMaxSchedules() + " schedules per provider)");
        }

        ProviderVehicleEntity vehicle = vehicleRepo.findById(req.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + req.getVehicleId()));
        if (!vehicle.getProvider().getId().equals(provider.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Vehicle does not belong to provider");
        }

        if (!Boolean.TRUE.equals(vehicle.getActive())) {
            throw new IllegalArgumentException("Cannot schedule an inactive vehicle. Activate the vehicle first.");
        }

        Integer seatsOffered = req.getSeatsOffered() != null ? req.getSeatsOffered() : vehicle.getSeatsTotal();
        if (seatsOffered > vehicle.getSeatsTotal()) {
            throw new IllegalArgumentException("Seats offered (" + seatsOffered +
                    ") cannot exceed vehicle's total capacity (" + vehicle.getSeatsTotal() + ")");
        }

        // Validate origin and destination cities
        CityInfo c1 = InventoryService.findCity(req.getOriginCity());
        CityInfo c2 = InventoryService.findCity(req.getDestinationCity());
        if (c1 == null) {
            throw new IllegalArgumentException("Origin city '" + req.getOriginCity() + "' is not supported");
        }
        if (c2 == null) {
            throw new IllegalArgumentException("Destination city '" + req.getDestinationCity() + "' is not supported");
        }

        if (c1.getKey().equalsIgnoreCase(c2.getKey())) {
            throw new IllegalArgumentException("Origin and destination cities cannot be the same");
        }

        // Validate dates
        LocalDate today = LocalDate.now();
        LocalDate validFrom = req.getValidFrom() != null ? req.getValidFrom() : today;
        LocalDate validTo = req.getValidTo() != null ? req.getValidTo() : today.plusDays(90);
        if (validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("Valid to date must be on or after valid from date");
        }
        if (validTo.isAfter(today.plusDays(90))) {
            throw new IllegalArgumentException("Valid to date cannot be more than 90 days in advance");
        }

        // Validate journey duration
        long journeyMinutes = calculateJourneyMinutes(req.getDepartureTime(), req.getArrivalTime(), req.getArrivesNextDay());
        if (journeyMinutes <= 0) {
            throw new IllegalArgumentException("Arrival time must be after departure time");
        }
        int maxAllowedMinutes = props.getMaxJourneyMinutes().getOrDefault(vehicle.getMode().name(), 2160);
        if (journeyMinutes > maxAllowedMinutes) {
            throw new IllegalArgumentException("Journey duration of " + (journeyMinutes / 60) + "h " + (journeyMinutes % 60) +
                    "m exceeds maximum allowed duration (" + (maxAllowedMinutes / 60) + "h) for " + vehicle.getMode());
        }

        // Validate price against distance and mode bounds
        double distanceKm = InventoryService.calculateDistance(c1, c2);
        validatePrice(req.getPrice(), vehicle.getMode(), distanceKm);

        // Check for overlapping schedules on the same vehicle
        checkScheduleOverlap(vehicle.getId(), null, validFrom, validTo,
                req.getDaysOfWeek(), req.getDepartureTime(), req.getArrivalTime(), req.getArrivesNextDay());

        String scheduleId = IdGenerator.generate("SCH");
        ProviderScheduleEntity schedule = ProviderScheduleEntity.builder()
                .id(scheduleId)
                .provider(provider)
                .vehicle(vehicle)
                .originCity(c1.getName())
                .destinationCity(c2.getName())
                .originStop((req.getOriginStop() != null && !req.getOriginStop().trim().isEmpty()) ? req.getOriginStop().trim() : c1.getName() + " Central")
                .destinationStop((req.getDestinationStop() != null && !req.getDestinationStop().trim().isEmpty()) ? req.getDestinationStop().trim() : c2.getName() + " Central")
                .departureTime(req.getDepartureTime())
                .arrivalTime(req.getArrivalTime())
                .arrivesNextDay(Boolean.TRUE.equals(req.getArrivesNextDay()))
                .price(req.getPrice().setScale(2, RoundingMode.HALF_UP))
                .seatsOffered(seatsOffered)
                .daysOfWeek(normaliseDaysOfWeek(req.getDaysOfWeek()))
                .validFrom(validFrom)
                .validTo(validTo)
                .reliability(props.getDefaultReliability())
                .active(req.getActive() != null ? req.getActive() : true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Create or update associated ServiceEntity in marketplace catalogue
        ServiceEntity service = ensureServiceEntityForSchedule(schedule, provider, vehicle);
        schedule.setServiceId(service.getId());

        ProviderScheduleEntity saved = scheduleRepo.save(schedule);

        // Materialize departures for the configured horizon (14 days)
        materializeDeparturesForSchedule(saved);

        log.info("Created provider schedule: id={}, route={} -> {}, vehicle={}",
                saved.getId(), saved.getOriginCity(), saved.getDestinationCity(), vehicle.getVehicleNumber());

        return toScheduleDto(saved);
    }

    @Transactional
    public ProviderScheduleDto updateSchedule(String scheduleId, ProviderScheduleRequest req) {
        ProviderEntity provider = requireCurrentProvider();
        checkWriteRateLimit(provider.getId());

        ProviderScheduleEntity schedule = scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found with ID: " + scheduleId));
        if (!schedule.getProvider().getId().equals(provider.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("You do not own this schedule");
        }

        ProviderVehicleEntity vehicle = vehicleRepo.findById(req.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + req.getVehicleId()));
        if (!vehicle.getProvider().getId().equals(provider.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Vehicle does not belong to provider");
        }

        Integer seatsOffered = req.getSeatsOffered() != null ? req.getSeatsOffered() : vehicle.getSeatsTotal();
        if (seatsOffered > vehicle.getSeatsTotal()) {
            throw new IllegalArgumentException("Seats offered (" + seatsOffered +
                    ") cannot exceed vehicle total capacity (" + vehicle.getSeatsTotal() + ")");
        }

        CityInfo c1 = InventoryService.findCity(req.getOriginCity());
        CityInfo c2 = InventoryService.findCity(req.getDestinationCity());
        if (c1 == null || c2 == null || c1.getKey().equalsIgnoreCase(c2.getKey())) {
            throw new IllegalArgumentException("Invalid origin or destination city");
        }

        LocalDate today = LocalDate.now();
        LocalDate validFrom = req.getValidFrom() != null ? req.getValidFrom() : (schedule.getValidFrom() != null ? schedule.getValidFrom() : today);
        LocalDate validTo = req.getValidTo() != null ? req.getValidTo() : (schedule.getValidTo() != null ? schedule.getValidTo() : today.plusDays(90));
        if (validTo.isBefore(validFrom) || validTo.isAfter(today.plusDays(90))) {
            throw new IllegalArgumentException("Invalid validity date range");
        }

        long journeyMinutes = calculateJourneyMinutes(req.getDepartureTime(), req.getArrivalTime(), req.getArrivesNextDay());
        if (journeyMinutes <= 0) {
            throw new IllegalArgumentException("Arrival time must be after departure time");
        }

        double distanceKm = InventoryService.calculateDistance(c1, c2);
        validatePrice(req.getPrice(), vehicle.getMode(), distanceKm);

        checkScheduleOverlap(vehicle.getId(), scheduleId, validFrom, validTo,
                req.getDaysOfWeek(), req.getDepartureTime(), req.getArrivalTime(), req.getArrivesNextDay());

        schedule.setVehicle(vehicle);
        schedule.setOriginCity(c1.getName());
        schedule.setDestinationCity(c2.getName());
        schedule.setOriginStop((req.getOriginStop() != null && !req.getOriginStop().trim().isEmpty()) ? req.getOriginStop().trim() : c1.getName() + " Central");
        schedule.setDestinationStop((req.getDestinationStop() != null && !req.getDestinationStop().trim().isEmpty()) ? req.getDestinationStop().trim() : c2.getName() + " Central");
        schedule.setDepartureTime(req.getDepartureTime());
        schedule.setArrivalTime(req.getArrivalTime());
        schedule.setArrivesNextDay(Boolean.TRUE.equals(req.getArrivesNextDay()));
        schedule.setPrice(req.getPrice().setScale(2, RoundingMode.HALF_UP));
        schedule.setSeatsOffered(seatsOffered);
        schedule.setDaysOfWeek(normaliseDaysOfWeek(req.getDaysOfWeek()));
        schedule.setValidFrom(validFrom);
        schedule.setValidTo(validTo);
        if (req.getActive() != null) {
            schedule.setActive(req.getActive());
        }

        // Update future departures:
        // "Editing a schedule affects only FUTURE departures that have no bookings; departures with bookings keep their original data."
        List<ServiceDepartureEntity> futureDeps = departureRepo.findByScheduleIdAndTravelDateGreaterThanEqual(scheduleId, today);
        int unchangedCount = 0;
        int updatedCount = 0;

        boolean hasBookedDeps = false;
        for (ServiceDepartureEntity dep : futureDeps) {
            long bookingCount = bookingRepo.countByDepartureId(dep.getId());
            if (bookingCount > 0) {
                unchangedCount++;
                hasBookedDeps = true;
            }
        }

        // If there are departures with bookings, assign a new ServiceEntity for future unbooked departures
        // so that departures with bookings retain the original ServiceEntity with unchanged price and times.
        if (hasBookedDeps) {
            schedule.setServiceId(IdGenerator.generate("SRV-PRV"));
        }

        // Update ServiceEntity
        ServiceEntity service = ensureServiceEntityForSchedule(schedule, provider, vehicle);
        schedule.setServiceId(service.getId());

        ProviderScheduleEntity saved = scheduleRepo.save(schedule);

        for (ServiceDepartureEntity dep : futureDeps) {
            long bookingCount = bookingRepo.countByDepartureId(dep.getId());
            if (bookingCount == 0) {
                // Safe to update seats and service
                dep.setSeatsAvailable(saved.getSeatsOffered());
                dep.setService(service);
                departureRepo.save(dep);
                updatedCount++;
            }
        }

        // Materialize any missing dates in horizon
        materializeDeparturesForSchedule(saved);

        String message = String.format("Schedule updated. %d future departure(s) updated. %d departure(s) with existing bookings were left unchanged.",
                updatedCount, unchangedCount);

        ProviderScheduleDto dto = toScheduleDto(saved);
        dto.setMessage(message);
        return dto;
    }

    @Transactional
    public void deleteSchedule(String scheduleId) {
        ProviderEntity provider = requireCurrentProvider();
        checkWriteRateLimit(provider.getId());

        ProviderScheduleEntity schedule = scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found with ID: " + scheduleId));
        if (!schedule.getProvider().getId().equals(provider.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("You do not own this schedule");
        }

        // Check if any future departure has bookings
        LocalDate today = LocalDate.now();
        List<ServiceDepartureEntity> futureDeps = departureRepo.findByScheduleIdAndTravelDateGreaterThanEqual(scheduleId, today);
        for (ServiceDepartureEntity dep : futureDeps) {
            if (bookingRepo.countByDepartureId(dep.getId()) > 0) {
                throw new ConflictException("Cannot delete schedule with active future bookings. You may deactivate it instead.");
            }
        }

        schedule.setActive(false);
        scheduleRepo.save(schedule);

        if (schedule.getServiceId() != null) {
            serviceRepo.findById(schedule.getServiceId()).ifPresent(s -> {
                s.setActive(false);
                serviceRepo.save(s);
            });
        }
    }

    // =========================================================================
    // 4. Departures & Status Reporting
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ProviderDepartureDto> getUpcomingDepartures() {
        ProviderEntity provider = requireCurrentProvider();
        List<ServiceDepartureEntity> deps = departureRepo.findByProviderIdOrderByTravelDateAsc(provider.getId());

        LocalDate today = LocalDate.now();
        return deps.stream()
                .filter(d -> !d.getTravelDate().isBefore(today))
                .map(d -> {
                    ServiceEntity s = d.getService();
                    long bookings = bookingRepo.countByDepartureId(d.getId());
                    int seatsTotal = s != null ? s.getSeatsTotal() : d.getSeatsAvailable();
                    int seatsSold = Math.max(0, seatsTotal - d.getSeatsAvailable());

                    return ProviderDepartureDto.builder()
                            .id(d.getId())
                            .scheduleId(d.getScheduleId())
                            .travelDate(d.getTravelDate())
                            .vehicleNumber(s != null ? s.getVehicleNumber() : "-")
                            .mode(s != null ? BookingType.valueOf(s.getMode()) : BookingType.BUS)
                            .originCity(s != null ? s.getOriginCity() : "-")
                            .destinationCity(s != null ? s.getDestinationCity() : "-")
                            .originStop(s != null ? s.getOriginStop() : "-")
                            .destinationStop(s != null ? s.getDestinationStop() : "-")
                            .departureTime(s != null ? s.getDepartureTime() : LocalTime.of(0, 0))
                            .arrivalTime(s != null ? s.getArrivalTime() : LocalTime.of(0, 0))
                            .seatsAvailable(d.getSeatsAvailable())
                            .seatsSold(seatsSold)
                            .seatsTotal(seatsTotal)
                            .status(d.getStatus())
                            .delayMinutes(d.getDelayMinutes())
                            .statusSource(d.getStatusSource())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProviderBookingDto> getDepartureBookings(String departureId) {
        ProviderEntity provider = requireCurrentProvider();

        ServiceDepartureEntity dep = departureRepo.findById(departureId)
                .orElseThrow(() -> new ResourceNotFoundException("Departure not found with ID: " + departureId));

        boolean isOwner = (dep.getProviderId() != null && provider.getId().equals(dep.getProviderId()))
                || (dep.getService() != null && provider.getId().equals(dep.getService().getProviderId()))
                || (dep.getScheduleId() != null && scheduleRepo.findById(dep.getScheduleId())
                .map(s -> provider.getId().equals(s.getProvider().getId())).orElse(false));

        if (!isOwner) {
            throw new org.springframework.security.access.AccessDeniedException("Cannot view bookings for another provider's departure");
        }

        List<BookingEntity> bookings = bookingRepo.findByDepartureId(departureId);

        // Strictly sanitize: booking reference, passenger names, seat numbers, and status ONLY!
        // No traveller email, phone, card data or trip details!
        return bookings.stream().map(b -> ProviderBookingDto.builder()
                .bookingReference(b.getBookingReference() != null ? b.getBookingReference() : b.getId())
                .passengerNames(b.getPassengerNames() != null ? b.getPassengerNames() : "Reserved")
                .seatNumbers(b.getSeatNumbers() != null ? b.getSeatNumbers() : "-")
                .status(b.getStatus())
                .cancellationTimestamp(b.getCancelledAt())
                .cancelledBy(b.getCancelledBy())
                .build()
        ).collect(Collectors.toList());
    }

    @Transactional
    public ProviderDepartureDto reportDepartureStatus(String departureId, ProviderReportStatusRequest request) {
        ProviderEntity provider = requireCurrentProvider();
        checkWriteRateLimit(provider.getId());

        ServiceDepartureEntity dep = departureRepo.findById(departureId)
                .orElseThrow(() -> new ResourceNotFoundException("Departure not found with ID: " + departureId));

        boolean isOwner = (dep.getProviderId() != null && provider.getId().equals(dep.getProviderId()))
                || (dep.getService() != null && provider.getId().equals(dep.getService().getProviderId()))
                || (dep.getScheduleId() != null && scheduleRepo.findById(dep.getScheduleId())
                .map(s -> provider.getId().equals(s.getProvider().getId())).orElse(false));

        if (!isOwner) {
            throw new org.springframework.security.access.AccessDeniedException("Cannot report status on another provider's departure");
        }

        // Auto-heal missing providerId on departure
        if (dep.getProviderId() == null) {
            dep.setProviderId(provider.getId());
            dep = departureRepo.save(dep);
        }

        LocalDate today = LocalDate.now();
        if (dep.getTravelDate().isBefore(today)) {
            throw new IllegalArgumentException("Cannot report status on past departures");
        }

        ServiceEntity s = dep.getService();
        LocalTime depTime = s != null ? s.getDepartureTime() : LocalTime.MIN;
        LocalDateTime depDateTime = LocalDateTime.of(dep.getTravelDate(), depTime);

        String newState = request.getState().trim().toUpperCase();
        if ("CANCELLED".equals(newState) && dep.getTravelDate().isBefore(today)) {
            throw new IllegalArgumentException("Cannot cancel a departure that has already departed");
        }

        if ("DELAYED".equals(newState)) {
            if (request.getDelayMinutes() == null || request.getDelayMinutes() < 1 || request.getDelayMinutes() > 1440) {
                throw new IllegalArgumentException("Delay minutes must be between 1 and 1440 for DELAYED status");
            }
        }

        dep.setStatus(newState);
        dep.setDelayMinutes("DELAYED".equals(newState) ? request.getDelayMinutes() : 0);
        dep.setStatusSource("PROVIDER_REPORTED");
        ServiceDepartureEntity saved = departureRepo.saveAndFlush(dep);

        // Write audit entry for provider
        recordAudit(AuditActor.USER, "PROVIDER_REPORTED_STATUS",
                String.format("Provider %s reported %s (delay: %d mins) on departure %s. Reason: %s",
                        provider.getBusinessName(), newState, dep.getDelayMinutes(), departureId, request.getReason()));

        log.info("Provider {} reported status {} (delay {}m) for departure {}",
                provider.getBusinessName(), newState, dep.getDelayMinutes(), departureId);

        // Trigger disruption check so affected trips receive auto-detected disruptions & notifications
        try {
            statusCheckService.runStatusChecksForDeparture(departureId, true);
        } catch (Exception e) {
            log.warn("Error running immediate status check cycle after provider status report: {}", e.getMessage());
        }

        int seatsTotal = s != null ? s.getSeatsTotal() : saved.getSeatsAvailable();
        int seatsSold = Math.max(0, seatsTotal - saved.getSeatsAvailable());

        return ProviderDepartureDto.builder()
                .id(saved.getId())
                .scheduleId(saved.getScheduleId())
                .travelDate(saved.getTravelDate())
                .vehicleNumber(s != null ? s.getVehicleNumber() : "-")
                .mode(s != null ? BookingType.valueOf(s.getMode()) : BookingType.BUS)
                .originCity(s != null ? s.getOriginCity() : "-")
                .destinationCity(s != null ? s.getDestinationCity() : "-")
                .originStop(s != null ? s.getOriginStop() : "-")
                .destinationStop(s != null ? s.getDestinationStop() : "-")
                .departureTime(s != null ? s.getDepartureTime() : LocalTime.of(0, 0))
                .arrivalTime(s != null ? s.getArrivalTime() : LocalTime.of(0, 0))
                .seatsAvailable(saved.getSeatsAvailable())
                .seatsSold(seatsSold)
                .seatsTotal(seatsTotal)
                .status(saved.getStatus())
                .delayMinutes(saved.getDelayMinutes())
                .statusSource(saved.getStatusSource())
                .build();
    }

    @Transactional
    public ProviderDepartureDto offerAlternativeVehicle(String departureId, com.odyssey.dto.provider.ProviderOfferAlternativeRequest req) {
        ProviderEntity provider = requireCurrentProvider();
        checkWriteRateLimit(provider.getId());

        ServiceDepartureEntity problemDep = departureRepo.findById(departureId)
                .orElseThrow(() -> new ResourceNotFoundException("Departure not found with ID: " + departureId));

        boolean isProblemOwner = (problemDep.getProviderId() != null && provider.getId().equals(problemDep.getProviderId()))
                || (problemDep.getService() != null && provider.getId().equals(problemDep.getService().getProviderId()))
                || (problemDep.getScheduleId() != null && scheduleRepo.findById(problemDep.getScheduleId())
                .map(s -> provider.getId().equals(s.getProvider().getId())).orElse(false));

        if (!isProblemOwner) {
            throw new org.springframework.security.access.AccessDeniedException("Cannot offer alternative for another provider's departure");
        }

        ServiceEntity problemService = problemDep.getService();
        String mode = problemService != null && problemService.getMode() != null ? problemService.getMode().toUpperCase() : "";
        if (!"BUS".equals(mode) && !"TRAIN".equals(mode) && !"FLIGHT".equals(mode)) {
            throw new IllegalArgumentException("Alternative vehicle re-routing is only available for scheduled transport (Bus, Train, Flight).");
        }

        ServiceDepartureEntity altDep;

        String rawAltId = req.getAlternativeDepartureId() != null ? req.getAlternativeDepartureId().trim() : "";
        final String targetVehicleId = rawAltId.startsWith("VEHICLE:")
                ? rawAltId.substring(8).trim()
                : (req.getAlternativeVehicleId() != null ? req.getAlternativeVehicleId().trim() : "");

        if (!targetVehicleId.isBlank()) {
            // Option B: Deploy from existing registered fleet vehicle
            ProviderVehicleEntity vehicle = vehicleRepo.findByIdAndProviderId(targetVehicleId, provider.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Fleet vehicle not found with ID: " + targetVehicleId));
            if (Boolean.FALSE.equals(vehicle.getActive())) {
                throw new IllegalArgumentException("Selected fleet vehicle is inactive.");
            }
            if (problemService != null && vehicle.getVehicleNumber().equalsIgnoreCase(problemService.getVehicleNumber())) {
                throw new IllegalArgumentException("Cannot offer the same problem vehicle as its own alternative.");
            }
            altDep = createReplacementDepartureForVehicle(provider, problemDep, problemService, vehicle);
        } else if ("NEW_VEHICLE".equalsIgnoreCase(rawAltId) || (req.getReplacementVehicleNumber() != null && !req.getReplacementVehicleNumber().isBlank())) {
            // Option C: Register & deploy a backup vehicle on-demand
            String vNum = req.getReplacementVehicleNumber() != null ? req.getReplacementVehicleNumber().trim().toUpperCase() : "";
            if (vNum.isBlank()) {
                throw new IllegalArgumentException("Replacement vehicle number is required.");
            }
            if (problemService != null && vNum.equalsIgnoreCase(problemService.getVehicleNumber())) {
                throw new IllegalArgumentException("Replacement vehicle number cannot be the same as the problem vehicle.");
            }
            int seats = (req.getReplacementSeatsTotal() != null && req.getReplacementSeatsTotal() > 0)
                    ? req.getReplacementSeatsTotal()
                    : (problemService != null ? problemService.getSeatsTotal() : 40);
            String vType = (req.getReplacementVehicleType() != null && !req.getReplacementVehicleType().isBlank())
                    ? req.getReplacementVehicleType().trim()
                    : (problemService != null ? (problemService.getMode() + " Replacement") : "Bus");

            ProviderVehicleEntity vehicle = vehicleRepo.findByProviderIdOrderByCreatedAtDesc(provider.getId()).stream()
                    .filter(v -> vNum.equalsIgnoreCase(v.getVehicleNumber()))
                    .findFirst()
                    .orElseGet(() -> vehicleRepo.save(ProviderVehicleEntity.builder()
                            .id(IdGenerator.generate("VEH"))
                            .provider(provider)
                            .mode(problemService != null ? BookingType.valueOf(problemService.getMode()) : BookingType.BUS)
                            .vehicleNumber(vNum)
                            .vehicleType(vType)
                            .seatsTotal(seats)
                            .active(true)
                            .createdAt(LocalDateTime.now())
                            .build()));

            altDep = createReplacementDepartureForVehicle(provider, problemDep, problemService, vehicle);
        } else {
            // Option A: Existing departure ID
            if (rawAltId.isBlank()) {
                throw new IllegalArgumentException("Alternative departure ID or fleet vehicle is required");
            }

            altDep = departureRepo.findById(rawAltId)
                    .orElseThrow(() -> new ResourceNotFoundException("Alternative departure not found with ID: " + rawAltId));

            boolean isAltOwner = (altDep.getProviderId() != null && provider.getId().equals(altDep.getProviderId()))
                    || (altDep.getService() != null && provider.getId().equals(altDep.getService().getProviderId()))
                    || (altDep.getScheduleId() != null && scheduleRepo.findById(altDep.getScheduleId())
                    .map(s -> provider.getId().equals(s.getProvider().getId())).orElse(false));

            if (!isAltOwner) {
                throw new org.springframework.security.access.AccessDeniedException("Cannot offer another provider's vehicle as an alternative");
            }

            ServiceEntity altService = altDep.getService();
            if (altService != null && problemService != null) {
                if (!problemDep.getTravelDate().equals(altDep.getTravelDate())) {
                    throw new IllegalArgumentException("Alternative departure must be on the same date as the problem departure.");
                }
                if (!problemService.getOriginCity().equalsIgnoreCase(altService.getOriginCity())
                        || !problemService.getDestinationCity().equalsIgnoreCase(altService.getDestinationCity())) {
                    throw new IllegalArgumentException("Alternative departure must be on the same route as the problem departure.");
                }
            }

            if (altDep.getSeatsAvailable() == null || altDep.getSeatsAvailable() <= 0) {
                throw new IllegalArgumentException("Selected alternative vehicle has no available seats.");
            }
        }

        // Mark problem departure as CANCELLED (affects ONLY this specific date departure)
        problemDep.setStatus("CANCELLED");
        problemDep.setStatusSource("PROVIDER_REPORTED");
        problemDep.setAlternativeDepartureId(altDep.getId());
        ServiceDepartureEntity saved = departureRepo.saveAndFlush(problemDep);

        // Update bookings on this problem departure with alternativeDepartureId
        List<BookingEntity> problemBookings = bookingRepo.findByDepartureId(departureId);
        for (BookingEntity b : problemBookings) {
            b.setAlternativeDepartureId(altDep.getId());
            bookingRepo.save(b);
        }
        bookingRepo.flush();

        recordAudit(AuditActor.USER, "PROVIDER_OFFERED_ALTERNATIVE",
                String.format("Provider %s offered alternative departure %s for problem departure %s",
                        provider.getBusinessName(), altDep.getId(), departureId));

        // Trigger immediate status check cycle so affected trips receive auto-detected disruptions & notifications
        try {
            statusCheckService.runStatusChecksForDeparture(departureId, true);
        } catch (Exception e) {
            log.warn("Error running immediate status check cycle after offering alternative: {}", e.getMessage());
        }

        int seatsTotal = problemService != null ? problemService.getSeatsTotal() : saved.getSeatsAvailable();
        int seatsSold = Math.max(0, seatsTotal - saved.getSeatsAvailable());

        return ProviderDepartureDto.builder()
                .id(saved.getId())
                .scheduleId(saved.getScheduleId())
                .travelDate(saved.getTravelDate())
                .vehicleNumber(problemService != null ? problemService.getVehicleNumber() : "-")
                .mode(problemService != null ? BookingType.valueOf(problemService.getMode()) : BookingType.BUS)
                .originCity(problemService != null ? problemService.getOriginCity() : "-")
                .destinationCity(problemService != null ? problemService.getDestinationCity() : "-")
                .originStop(problemService != null ? problemService.getOriginStop() : "-")
                .destinationStop(problemService != null ? problemService.getDestinationStop() : "-")
                .departureTime(problemService != null ? problemService.getDepartureTime() : LocalTime.of(0, 0))
                .arrivalTime(problemService != null ? problemService.getArrivalTime() : LocalTime.of(0, 0))
                .seatsAvailable(saved.getSeatsAvailable())
                .seatsSold(seatsSold)
                .seatsTotal(seatsTotal)
                .status(saved.getStatus())
                .delayMinutes(saved.getDelayMinutes() != null ? saved.getDelayMinutes() : 0)
                .statusSource(saved.getStatusSource())
                .build();
    }

    private ServiceDepartureEntity createReplacementDepartureForVehicle(
            ProviderEntity provider,
            ServiceDepartureEntity problemDep,
            ServiceEntity problemService,
            ProviderVehicleEntity vehicle) {

        int seats = vehicle.getSeatsTotal() != null ? vehicle.getSeatsTotal()
                : (problemService != null ? problemService.getSeatsTotal() : 40);
        String srvId = IdGenerator.generate("SVC");
        String depId = IdGenerator.generate("DEP");

        ServiceEntity altService = ServiceEntity.builder()
                .id(srvId)
                .operator(problemService != null ? problemService.getOperator() : null)
                .mode(problemService != null ? problemService.getMode() : "BUS")
                .vehicleNumber(vehicle.getVehicleNumber())
                .originCity(problemService != null ? problemService.getOriginCity() : "-")
                .destinationCity(problemService != null ? problemService.getDestinationCity() : "-")
                .originStop(problemService != null ? problemService.getOriginStop() : "-")
                .destinationStop(problemService != null ? problemService.getDestinationStop() : "-")
                .departureTime(problemService != null ? problemService.getDepartureTime() : LocalTime.of(8, 0))
                .arrivalTime(problemService != null ? problemService.getArrivalTime() : LocalTime.of(14, 0))
                .arrivesNextDay(problemService != null && Boolean.TRUE.equals(problemService.getArrivesNextDay()))
                .price(problemService != null ? problemService.getPrice() : BigDecimal.valueOf(500))
                .seatsTotal(seats)
                .reliabilityScore(problemService != null && problemService.getReliabilityScore() != null ? problemService.getReliabilityScore() : 92)
                .active(true)
                .source("PROVIDER")
                .providerId(provider.getId())
                .build();
        serviceRepo.save(altService);

        ServiceDepartureEntity altDep = ServiceDepartureEntity.builder()
                .id(depId)
                .service(altService)
                .scheduleId(problemDep.getScheduleId())
                .travelDate(problemDep.getTravelDate())
                .seatsAvailable(seats)
                .status("SCHEDULED")
                .statusSource("PROVIDER_REPORTED")
                .providerId(provider.getId())
                .build();
        return departureRepo.saveAndFlush(altDep);
    }

    // =========================================================================
    // 5. Materialisation Helpers
    // =========================================================================

    @Transactional
    public void materializeDeparturesForAllActiveSchedules() {
        List<ProviderScheduleEntity> activeSchedules = scheduleRepo.findByActiveTrue();
        for (ProviderScheduleEntity schedule : activeSchedules) {
            try {
                materializeDeparturesForSchedule(schedule);
            } catch (Exception e) {
                log.warn("Failed to materialize departures for schedule {}: {}", schedule.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void materializeDeparturesForProvider(String providerId) {
        List<ProviderScheduleEntity> schedules = scheduleRepo.findByProviderIdOrderByCreatedAtDesc(providerId);
        for (ProviderScheduleEntity schedule : schedules) {
            if (Boolean.TRUE.equals(schedule.getActive())) {
                materializeDeparturesForSchedule(schedule);
            }
        }
    }

    @Transactional
    public void materializeDeparturesForSchedule(ProviderScheduleEntity schedule) {
        if (!Boolean.TRUE.equals(schedule.getActive())) {
            return;
        }

        LocalDate today = LocalDate.now();
        int horizon = props.getHorizonDays() > 0 ? props.getHorizonDays() : 14;
        LocalDate horizonEnd = today.plusDays(horizon);

        LocalDate startDate = schedule.getValidFrom().isAfter(today) ? schedule.getValidFrom() : today;
        LocalDate endDate = schedule.getValidTo().isBefore(horizonEnd) ? schedule.getValidTo() : horizonEnd;

        if (startDate.isAfter(endDate)) {
            return;
        }

        Set<String> activeDays = parseDaysOfWeek(schedule.getDaysOfWeek());
        ServiceEntity service = serviceRepo.findById(schedule.getServiceId()).orElse(null);
        if (service == null) return;

        LocalDate curr = startDate;
        while (!curr.isAfter(endDate)) {
            String dayName = curr.getDayOfWeek().name().substring(0, 3); // MON, TUE, etc.
            if (activeDays.contains(dayName)) {
                // Check if departure already exists
                Optional<ServiceDepartureEntity> existingBySch = departureRepo.findByScheduleIdAndTravelDate(schedule.getId(), curr);
                Optional<ServiceDepartureEntity> existingBySrv = departureRepo.findByServiceIdAndTravelDate(service.getId(), curr);
                if (existingBySch.isPresent()) {
                    ServiceDepartureEntity d = existingBySch.get();
                    if (d.getProviderId() == null) {
                        d.setProviderId(schedule.getProvider().getId());
                        departureRepo.save(d);
                    }
                } else if (existingBySrv.isPresent()) {
                    ServiceDepartureEntity d = existingBySrv.get();
                    if (d.getProviderId() == null || d.getScheduleId() == null) {
                        d.setProviderId(schedule.getProvider().getId());
                        d.setScheduleId(schedule.getId());
                        departureRepo.save(d);
                    }
                } else {
                    ServiceDepartureEntity dep = ServiceDepartureEntity.builder()
                            .id(IdGenerator.generate("DEP"))
                            .service(service)
                            .travelDate(curr)
                            .seatsAvailable(schedule.getSeatsOffered())
                            .status("SCHEDULED")
                            .delayMinutes(0)
                            .source("PROVIDER")
                            .providerId(schedule.getProvider().getId())
                            .scheduleId(schedule.getId())
                            .build();
                    departureRepo.save(dep);
                }
            }
            curr = curr.plusDays(1);
        }
    }

    // =========================================================================
    // 6. Security & Internal Helpers
    // =========================================================================

    public ProviderEntity requireCurrentProvider() {
        UserEntity currentUser = authService.getCurrentUserEntity();
        if (currentUser == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }
        return providerRepo.findByUserId(currentUser.getId())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("User is not a registered provider"));
    }

    private void checkWriteRateLimit(String key) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(RATE_LIMIT_WINDOW);

        writeRateLimits.compute(key, (k, timestamps) -> {
            if (timestamps == null) {
                timestamps = new ArrayList<>();
            }
            timestamps.removeIf(t -> t.isBefore(cutoff));
            if (timestamps.size() >= props.getRateLimitPerMinute()) {
                throw new RateLimitException("Provider write rate limit exceeded (maximum " +
                        props.getRateLimitPerMinute() + " requests per minute)");
            }
            timestamps.add(now);
            return timestamps;
        });
    }

    private void checkRateLimit(String key) {
        checkWriteRateLimit(key);
    }

    private int getMaxBookedSeatsInFuture(String vehicleId) {
        List<ProviderScheduleEntity> schedules = scheduleRepo.findByVehicleIdAndActiveTrue(vehicleId);
        int maxBooked = 0;
        LocalDate today = LocalDate.now();

        for (ProviderScheduleEntity s : schedules) {
            List<ServiceDepartureEntity> futureDeps = departureRepo.findByScheduleIdAndTravelDateGreaterThanEqual(s.getId(), today);
            for (ServiceDepartureEntity dep : futureDeps) {
                int seatsOffered = s.getSeatsOffered() != null ? s.getSeatsOffered() : 0;
                int available = dep.getSeatsAvailable();
                int bookedFromInventory = Math.max(0, seatsOffered - available);

                int bookedFromRecords = 0;
                if (bookingRepo != null) {
                    List<BookingEntity> bookings = bookingRepo.findByDepartureId(dep.getId());
                    for (BookingEntity b : bookings) {
                        if (!"CANCELLED".equalsIgnoreCase(b.getStatus())) {
                            if (b.getSeatNumbers() != null && !b.getSeatNumbers().isBlank()) {
                                bookedFromRecords += b.getSeatNumbers().split(",").length;
                            } else {
                                bookedFromRecords += 1;
                            }
                        }
                    }
                }

                int booked = Math.max(bookedFromInventory, bookedFromRecords);
                if (booked > maxBooked) {
                    maxBooked = booked;
                }
            }
        }
        return maxBooked;
    }

    private ServiceEntity ensureServiceEntityForSchedule(ProviderScheduleEntity schedule, ProviderEntity provider, ProviderVehicleEntity vehicle) {
        String serviceId = schedule.getServiceId() != null ? schedule.getServiceId() : "SRV-PRV-" + schedule.getId();

        // Ensure Operator exists for provider
        OperatorEntity op = operatorRepo.findByName(provider.getBusinessName()).orElseGet(() ->
                operatorRepo.save(OperatorEntity.builder()
                        .id("OP-PRV-" + provider.getId())
                        .name(provider.getBusinessName())
                        .mode(vehicle.getMode().name())
                        .contact(provider.getContactEmail())
                        .build())
        );

        ServiceEntity service = serviceRepo.findById(serviceId).orElseGet(() ->
                ServiceEntity.builder()
                        .id(serviceId)
                        .operator(op)
                        .source("PROVIDER")
                        .providerId(provider.getId())
                        .scheduleId(schedule.getId())
                        .build()
        );

        service.setOperator(op);
        service.setMode(vehicle.getMode().name());
        service.setVehicleNumber(vehicle.getVehicleNumber());
        service.setOriginCity(schedule.getOriginCity());
        service.setDestinationCity(schedule.getDestinationCity());
        service.setOriginStop(schedule.getOriginStop());
        service.setDestinationStop(schedule.getDestinationStop());
        service.setDepartureTime(schedule.getDepartureTime());
        service.setArrivalTime(schedule.getArrivalTime());
        service.setArrivesNextDay(schedule.getArrivesNextDay());
        service.setPrice(schedule.getPrice());
        service.setSeatsTotal(schedule.getSeatsOffered());
        service.setReliabilityScore(schedule.getReliability());
        service.setActive(Boolean.TRUE.equals(schedule.getActive()) && provider.getStatus() != ProviderStatus.SUSPENDED && provider.getStatus() != ProviderStatus.REJECTED);
        service.setSource("PROVIDER");
        service.setProviderId(provider.getId());
        service.setScheduleId(schedule.getId());

        return serviceRepo.save(service);
    }

    private void checkScheduleOverlap(String vehicleId, String excludeScheduleId, LocalDate validFrom, LocalDate validTo,
                                      String daysOfWeekStr, LocalTime depTime, LocalTime arrTime, Boolean arrivesNextDay) {
        List<ProviderScheduleEntity> existing = scheduleRepo.findByVehicleIdAndActiveTrue(vehicleId);
        Set<String> newDays = parseDaysOfWeek(daysOfWeekStr);

        for (ProviderScheduleEntity other : existing) {
            if (excludeScheduleId != null && other.getId().equals(excludeScheduleId)) {
                continue;
            }

            // Check date range overlap
            boolean datesOverlap = !validFrom.isAfter(other.getValidTo()) && !validTo.isBefore(other.getValidFrom());
            if (!datesOverlap) continue;

            // Check days of week overlap
            Set<String> otherDays = parseDaysOfWeek(other.getDaysOfWeek());
            Set<String> sharedDays = new HashSet<>(newDays);
            sharedDays.retainAll(otherDays);

            if (sharedDays.isEmpty()) continue;

            // Check time overlap on shared days
            boolean timeOverlaps = isTimeRangeOverlapping(depTime, arrTime, arrivesNextDay,
                    other.getDepartureTime(), other.getArrivalTime(), other.getArrivesNextDay());

            if (timeOverlaps) {
                throw new IllegalArgumentException("Vehicle has an overlapping schedule (" + other.getOriginCity() + " -> " +
                        other.getDestinationCity() + ", " + other.getDepartureTime() + " - " + other.getArrivalTime() +
                        ") on shared days: " + String.join(", ", sharedDays));
            }
        }
    }

    private boolean isTimeRangeOverlapping(LocalTime d1, LocalTime a1, Boolean n1, LocalTime d2, LocalTime a2, Boolean n2) {
        int start1 = d1.getHour() * 60 + d1.getMinute();
        int end1 = a1.getHour() * 60 + a1.getMinute() + (Boolean.TRUE.equals(n1) ? 1440 : 0);

        int start2 = d2.getHour() * 60 + d2.getMinute();
        int end2 = a2.getHour() * 60 + a2.getMinute() + (Boolean.TRUE.equals(n2) ? 1440 : 0);

        return Math.max(start1, start2) < Math.min(end1, end2);
    }

    private long calculateJourneyMinutes(LocalTime depTime, LocalTime arrTime, Boolean arrivesNextDay) {
        if (Boolean.TRUE.equals(arrivesNextDay)) {
            return Duration.between(depTime, arrTime).plusDays(1).toMinutes();
        } else {
            return Duration.between(depTime, arrTime).toMinutes();
        }
    }

    private void validatePrice(BigDecimal price, BookingType mode, double distanceKm) {
        ProviderPortalProperties.ModePricingRule rule = props.getPricingRules().get(mode.name());
        if (rule == null) {
            rule = new ProviderPortalProperties.ModePricingRule();
        }

        BigDecimal minCalculated = rule.getMinPerKm().multiply(BigDecimal.valueOf(distanceKm));
        BigDecimal maxCalculated = rule.getMaxPerKm().multiply(BigDecimal.valueOf(distanceKm));

        BigDecimal effectiveMin = minCalculated.max(rule.getMinPrice());
        BigDecimal effectiveMax = maxCalculated.min(rule.getMaxPrice());

        if (price.compareTo(effectiveMin) < 0 || price.compareTo(effectiveMax) > 0) {
            throw new IllegalArgumentException(String.format(
                    "Price ₹%.2f is outside acceptable range for %s (₹%.2f - ₹%.2f for ~%.0f km)",
                    price, mode.name(), effectiveMin, effectiveMax, distanceKm));
        }
    }

    private String normaliseDay(String day) {
        if (day == null) return "";
        day = day.trim().toUpperCase();
        if (day.startsWith("MON")) return "MON";
        if (day.startsWith("TUE")) return "TUE";
        if (day.startsWith("WED")) return "WED";
        if (day.startsWith("THU")) return "THU";
        if (day.startsWith("FRI")) return "FRI";
        if (day.startsWith("SAT")) return "SAT";
        if (day.startsWith("SUN")) return "SUN";
        return day;
    }

    private String normaliseDaysOfWeek(String days) {
        if (days == null || days.isBlank()) {
            return "MON,TUE,WED,THU,FRI,SAT,SUN";
        }
        return Arrays.stream(days.split(","))
                .map(this::normaliseDay)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private Set<String> parseDaysOfWeek(String days) {
        if (days == null || days.isBlank()) {
            return Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
        }
        return Arrays.stream(days.split(","))
                .map(this::normaliseDay)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private void recordAudit(AuditActor actor, String action, String details) {
        auditRepo.save(AuditEntryEntity.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(actor != null ? actor.name() : "SYSTEM")
                .action(action)
                .details(details)
                .build());
    }

    private void recordAudit(String action, String details) {
        recordAudit(AuditActor.USER, action, details);
    }

    public ProviderResponse toResponse(ProviderEntity entity) {
        if (entity == null) return null;
        long vehicleCount = vehicleRepo != null ? vehicleRepo.countByProviderId(entity.getId()) : 0;
        long hotelCount = hotelRepo != null ? hotelRepo.countByProviderId(entity.getId()) : 0;
        long cabServiceCount = cabRepo != null ? cabRepo.countByProviderId(entity.getId()) : 0;
        long bookingCount = bookingRepo != null ? bookingRepo.countByProviderId(entity.getId()) : 0;

        return ProviderResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .businessName(entity.getBusinessName())
                .businessType(entity.getBusinessType())
                .contactEmail(entity.getContactEmail())
                .contactPhone(entity.getContactPhone())
                .city(entity.getCity())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .reviewedBy(entity.getReviewedBy())
                .reviewedAt(entity.getReviewedAt())
                .statusReason(entity.getStatusReason())
                .vehicleCount(vehicleCount)
                .hotelCount(hotelCount)
                .cabServiceCount(cabServiceCount)
                .bookingCount(bookingCount)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ProviderVehicleDto toVehicleDto(ProviderVehicleEntity v, int maxBooked) {
        return ProviderVehicleDto.builder()
                .id(v.getId())
                .providerId(v.getProvider() != null ? v.getProvider().getId() : null)
                .mode(v.getMode())
                .vehicleNumber(v.getVehicleNumber())
                .vehicleType(v.getVehicleType())
                .seatsTotal(v.getSeatsTotal())
                .amenities(v.getAmenities())
                .active(v.getActive())
                .createdAt(v.getCreatedAt())
                .maxBookedSeatsInFuture(maxBooked)
                .build();
    }

    private ProviderScheduleDto toScheduleDto(ProviderScheduleEntity s) {
        return ProviderScheduleDto.builder()
                .id(s.getId())
                .providerId(s.getProvider() != null ? s.getProvider().getId() : null)
                .vehicleId(s.getVehicle() != null ? s.getVehicle().getId() : null)
                .vehicleNumber(s.getVehicle() != null ? s.getVehicle().getVehicleNumber() : null)
                .mode(s.getVehicle() != null ? s.getVehicle().getMode() : null)
                .serviceId(s.getServiceId())
                .originCity(s.getOriginCity())
                .destinationCity(s.getDestinationCity())
                .originStop(s.getOriginStop())
                .destinationStop(s.getDestinationStop())
                .departureTime(s.getDepartureTime())
                .arrivalTime(s.getArrivalTime())
                .arrivesNextDay(s.getArrivesNextDay())
                .price(s.getPrice())
                .seatsOffered(s.getSeatsOffered())
                .daysOfWeek(s.getDaysOfWeek())
                .validFrom(s.getValidFrom())
                .validTo(s.getValidTo())
                .reliability(s.getReliability())
                .active(s.getActive())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    // =========================================================================
    // 6. Provider Hotels CRUD (Phase 18B)
    // =========================================================================

    private void assertCanManageHotels(ProviderEntity provider) {
        if (provider.getBusinessType() != BusinessType.HOTEL && provider.getBusinessType() != BusinessType.BOTH) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Your business type (" + provider.getBusinessType() + ") does not permit hotel or room operations");
        }
    }

    @Transactional(readOnly = true)
    public List<ProviderHotelResponse> getHotels() {
        ProviderEntity provider = requireCurrentProvider();
        assertCanManageHotels(provider);
        List<HotelEntity> hotels = hotelRepo.findByProviderIdOrderByCreatedAtDesc(provider.getId());
        return hotels.stream().map(this::toHotelResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProviderHotelResponse getHotel(String id) {
        ProviderEntity provider = requireCurrentProvider();
        assertCanManageHotels(provider);
        HotelEntity hotel = hotelRepo.findByIdAndProviderId(id, provider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found or not owned by you: " + id));
        return toHotelResponse(hotel);
    }

    @Transactional
    public ProviderHotelResponse createHotel(ProviderHotelRequest req) {
        ProviderEntity provider = requireCurrentProvider();
        checkRateLimit(provider.getId());
        assertCanManageHotels(provider);

        if (hotelRepo.countByProviderId(provider.getId()) >= props.getMaxHotels()) {
            throw new IllegalArgumentException("Maximum of " + props.getMaxHotels() + " hotels reached per provider");
        }

        CityInfo city = InventoryService.findCity(req.getCity());
        if (city == null) {
            throw new IllegalArgumentException("City not found in supported catalogue: " + req.getCity());
        }

        String hotelId = IdGenerator.generate("HTL");
        HotelEntity hotel = HotelEntity.builder()
                .id(hotelId)
                .providerId(provider.getId())
                .name(req.getName().trim())
                .city(city.getKey())
                .address(req.getAddress().trim())
                .description(req.getDescription() != null ? req.getDescription().trim() : null)
                .contactEmail(req.getContactEmail().trim())
                .checkInTime(req.getCheckInTime() != null ? req.getCheckInTime() : "14:00")
                .checkOutTime(req.getCheckOutTime() != null ? req.getCheckOutTime() : "11:00")
                .rating(null) // NOT editable by provider
                .pricePerNight(BigDecimal.ZERO)
                .roomsTotal(0)
                .source("PROVIDER")
                .active(req.getActive() != null ? req.getActive() : true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        HotelEntity saved = hotelRepo.save(hotel);
        recordAudit("PROVIDER_HOTEL_CREATED", "Created hotel " + saved.getName() + " in " + saved.getCity());
        return toHotelResponse(saved);
    }

    @Transactional
    public ProviderHotelResponse updateHotel(String id, ProviderHotelRequest req) {
        ProviderEntity provider = requireCurrentProvider();
        checkRateLimit(provider.getId());
        assertCanManageHotels(provider);

        HotelEntity hotel = hotelRepo.findByIdAndProviderId(id, provider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found or not owned by you: " + id));

        CityInfo city = InventoryService.findCity(req.getCity());
        if (city == null) {
            throw new IllegalArgumentException("City not found in supported catalogue: " + req.getCity());
        }

        hotel.setName(req.getName().trim());
        hotel.setCity(city.getKey());
        hotel.setAddress(req.getAddress().trim());
        hotel.setDescription(req.getDescription() != null ? req.getDescription().trim() : null);
        hotel.setContactEmail(req.getContactEmail().trim());
        if (req.getCheckInTime() != null) hotel.setCheckInTime(req.getCheckInTime());
        if (req.getCheckOutTime() != null) hotel.setCheckOutTime(req.getCheckOutTime());
        if (req.getActive() != null) hotel.setActive(req.getActive());
        hotel.setUpdatedAt(LocalDateTime.now());

        HotelEntity saved = hotelRepo.save(hotel);
        recordAudit("PROVIDER_HOTEL_UPDATED", "Updated hotel " + saved.getName() + " (ID: " + id + ")");
        return toHotelResponse(saved);
    }

    @Transactional
    public void deleteHotel(String id) {
        ProviderEntity provider = requireCurrentProvider();
        checkRateLimit(provider.getId());
        assertCanManageHotels(provider);

        HotelEntity hotel = hotelRepo.findByIdAndProviderId(id, provider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found or not owned by you: " + id));

        // Future booking conflict check
        boolean hasFutureBookings = bookingRepo.findAll().stream()
                .filter(b -> "HOTEL".equalsIgnoreCase(b.getType()))
                .filter(b -> (id.equals(b.getServiceId()) || hotel.getName().equalsIgnoreCase(b.getProvider()) || (b.getProviderId() != null && b.getProviderId().equals(provider.getId()))))
                .filter(b -> b.getEndTime() != null && b.getEndTime().isAfter(LocalDateTime.now()))
                .anyMatch(b -> !"CANCELLED".equalsIgnoreCase(b.getStatus()));

        boolean hasBookedAvailability = availabilityRepo.findByHotelIdAndStayDateGreaterThanEqual(id, LocalDate.now()).stream()
                .anyMatch(a -> a.getRoomsBooked() > 0);

        if (hasFutureBookings || hasBookedAvailability) {
            throw new ConflictException("Cannot delete hotel with future active bookings. You may deactivate it instead to stop new reservations.");
        }

        hotelRepo.delete(hotel);
        recordAudit("PROVIDER_HOTEL_DELETED", "Deleted hotel " + hotel.getName() + " (ID: " + id + ")");
    }

    @Transactional
    public void toggleHotelStatus(String id, boolean active) {
        ProviderEntity provider = requireCurrentProvider();
        assertCanManageHotels(provider);
        HotelEntity hotel = hotelRepo.findByIdAndProviderId(id, provider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found: " + id));
        hotel.setActive(active);
        hotel.setUpdatedAt(LocalDateTime.now());
        hotelRepo.save(hotel);
    }

    // =========================================================================
    // 7. Hotel Room Types & Date-based Availability (Phase 18B)
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ProviderRoomTypeResponse> getRoomTypes(String hotelId) {
        ProviderEntity provider = requireCurrentProvider();
        assertCanManageHotels(provider);
        hotelRepo.findByIdAndProviderId(hotelId, provider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found: " + hotelId));

        return roomTypeRepo.findByHotelIdOrderByCreatedAtAsc(hotelId).stream()
                .map(this::toRoomTypeResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProviderRoomTypeResponse createRoomType(String hotelId, ProviderRoomTypeRequest req) {
        ProviderEntity provider = requireCurrentProvider();
        checkRateLimit(provider.getId());
        assertCanManageHotels(provider);

        HotelEntity hotel = hotelRepo.findByIdAndProviderId(hotelId, provider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found: " + hotelId));

        if (roomTypeRepo.countByHotelId(hotelId) >= props.getMaxRoomTypesPerHotel()) {
            throw new IllegalArgumentException("Maximum of " + props.getMaxRoomTypesPerHotel() + " room types reached for this hotel");
        }

        String roomTypeId = IdGenerator.generate("RMT");
        HotelRoomTypeEntity roomType = HotelRoomTypeEntity.builder()
                .id(roomTypeId)
                .hotelId(hotelId)
                .name(req.getName().trim())
                .maxGuests(req.getMaxGuests())
                .pricePerNight(req.getPricePerNight())
                .roomsTotal(req.getRoomsTotal())
                .amenities(req.getAmenities() != null ? req.getAmenities().trim() : null)
                .active(req.getActive() != null ? req.getActive() : true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        HotelRoomTypeEntity saved = roomTypeRepo.save(roomType);

        // Update hotel base price / rooms if first room type
        if (hotel.getPricePerNight() == null || hotel.getPricePerNight().compareTo(BigDecimal.ZERO) == 0) {
            hotel.setPricePerNight(saved.getPricePerNight());
            hotel.setRoomsTotal(saved.getRoomsTotal());
            hotelRepo.save(hotel);
        }

        // Materialize availability for next N days idempotently
        materializeRoomAvailability(saved.getId(), hotelId, saved.getRoomsTotal());

        recordAudit("PROVIDER_ROOM_TYPE_CREATED", "Created room type " + saved.getName() + " for hotel " + hotel.getName());
        return toRoomTypeResponse(saved);
    }

    @Transactional
    public ProviderRoomTypeResponse updateRoomType(String hotelId, String roomId, ProviderRoomTypeRequest req) {
        ProviderEntity provider = requireCurrentProvider();
        checkRateLimit(provider.getId());
        assertCanManageHotels(provider);

        hotelRepo.findByIdAndProviderId(hotelId, provider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found: " + hotelId));

        HotelRoomTypeEntity roomType = roomTypeRepo.findByIdAndHotelId(roomId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Room type not found: " + roomId));

        // Check shrinking rooms below booked count
        if (req.getRoomsTotal() < roomType.getRoomsTotal()) {
            int maxBooked = availabilityRepo.findByRoomTypeIdAndStayDateGreaterThanEqual(roomId, LocalDate.now()).stream()
                    .mapToInt(HotelRoomAvailabilityEntity::getRoomsBooked)
                    .max().orElse(0);
            if (req.getRoomsTotal() < maxBooked) {
                throw new ConflictException("Cannot reduce rooms total to " + req.getRoomsTotal() + ", which is below currently booked count of " + maxBooked + " on future dates.");
            }
        }

        roomType.setName(req.getName().trim());
        roomType.setMaxGuests(req.getMaxGuests());
        roomType.setPricePerNight(req.getPricePerNight());
        roomType.setRoomsTotal(req.getRoomsTotal());
        roomType.setAmenities(req.getAmenities() != null ? req.getAmenities().trim() : null);
        if (req.getActive() != null) roomType.setActive(req.getActive());
        roomType.setUpdatedAt(LocalDateTime.now());

        HotelRoomTypeEntity saved = roomTypeRepo.save(roomType);

        // Update future availability records with new roomsTotal where roomsBooked <= newRoomsTotal
        materializeRoomAvailability(saved.getId(), hotelId, saved.getRoomsTotal());

        recordAudit("PROVIDER_ROOM_TYPE_UPDATED", "Updated room type " + saved.getName() + " (ID: " + roomId + ")");
        return toRoomTypeResponse(saved);
    }

    @Transactional
    public void deleteRoomType(String hotelId, String roomId) {
        ProviderEntity provider = requireCurrentProvider();
        checkRateLimit(provider.getId());
        assertCanManageHotels(provider);

        hotelRepo.findByIdAndProviderId(hotelId, provider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found: " + hotelId));

        HotelRoomTypeEntity roomType = roomTypeRepo.findByIdAndHotelId(roomId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Room type not found: " + roomId));

        // Future booking conflict check
        boolean hasFutureBookings = bookingRepo.findAll().stream()
                .filter(b -> "HOTEL".equalsIgnoreCase(b.getType()))
                .filter(b -> roomId.equals(b.getHotelRoomTypeId()))
                .filter(b -> b.getEndTime() != null && b.getEndTime().isAfter(LocalDateTime.now()))
                .anyMatch(b -> !"CANCELLED".equalsIgnoreCase(b.getStatus()));

        boolean hasBookedAvailability = availabilityRepo.findByRoomTypeIdAndStayDateGreaterThanEqual(roomId, LocalDate.now())
                .stream().anyMatch(a -> a.getRoomsBooked() != null && a.getRoomsBooked() > 0);

        if (hasFutureBookings || hasBookedAvailability) {
            throw new ConflictException("Cannot delete room type with future active bookings.");
        }

        roomTypeRepo.delete(roomType);
        recordAudit("PROVIDER_ROOM_TYPE_DELETED", "Deleted room type " + roomType.getName() + " (ID: " + roomId + ")");
    }

    @Transactional
    public List<HotelRoomAvailabilityEntity> getRoomAvailability(String hotelId, String roomId) {
        ProviderEntity provider = requireCurrentProvider();
        assertCanManageHotels(provider);
        hotelRepo.findByIdAndProviderId(hotelId, provider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found: " + hotelId));

        HotelRoomTypeEntity roomType = roomTypeRepo.findByIdAndHotelId(roomId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Room type not found: " + roomId));

        LocalDate today = LocalDate.now();
        LocalDate end = today.plusDays(props.getHorizonDays());
        materializeRoomAvailability(roomId, hotelId, roomType.getRoomsTotal());

        return availabilityRepo.findByRoomTypeIdAndStayDateBetweenOrderByStayDateAsc(roomId, today, end);
    }

    @Transactional
    public void updateRoomAvailability(String hotelId, String roomId, ProviderAvailabilityUpdateDto req) {
        ProviderEntity provider = requireCurrentProvider();
        checkRateLimit(provider.getId());
        assertCanManageHotels(provider);

        hotelRepo.findByIdAndProviderId(hotelId, provider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found: " + hotelId));

        HotelRoomTypeEntity roomType = roomTypeRepo.findByIdAndHotelId(roomId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Room type not found: " + roomId));

        HotelRoomAvailabilityEntity avail = availabilityRepo.findByRoomTypeIdAndStayDate(roomId, req.getStayDate())
                .orElseGet(() -> HotelRoomAvailabilityEntity.builder()
                        .id(IdGenerator.generate("AVA"))
                        .roomTypeId(roomId)
                        .hotelId(hotelId)
                        .stayDate(req.getStayDate())
                        .roomsTotal(roomType.getRoomsTotal())
                        .roomsBooked(0)
                        .isClosed(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build());

        avail.setIsClosed(req.getIsClosed());
        avail.setUpdatedAt(LocalDateTime.now());
        availabilityRepo.save(avail);

        recordAudit("PROVIDER_AVAILABILITY_UPDATED", "Updated date " + req.getStayDate() + " closed=" + req.getIsClosed() + " for room " + roomType.getName());
    }

    @Transactional
    public void materializeRoomAvailability(String roomTypeId, String hotelId, int roomsTotal) {
        LocalDate today = LocalDate.now();
        LocalDate horizonEnd = today.plusDays(props.getHorizonDays());

        for (LocalDate date = today; !date.isAfter(horizonEnd); date = date.plusDays(1)) {
            Optional<HotelRoomAvailabilityEntity> opt = availabilityRepo.findByRoomTypeIdAndStayDate(roomTypeId, date);
            if (opt.isPresent()) {
                HotelRoomAvailabilityEntity existing = opt.get();
                if (existing.getRoomsBooked() <= roomsTotal) {
                    existing.setRoomsTotal(roomsTotal);
                    existing.setUpdatedAt(LocalDateTime.now());
                    availabilityRepo.save(existing);
                }
            } else {
                HotelRoomAvailabilityEntity newAvail = HotelRoomAvailabilityEntity.builder()
                        .id(IdGenerator.generate("AVA"))
                        .roomTypeId(roomTypeId)
                        .hotelId(hotelId)
                        .stayDate(date)
                        .roomsTotal(roomsTotal)
                        .roomsBooked(0)
                        .isClosed(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                availabilityRepo.save(newAvail);
            }
        }
    }

    // =========================================================================
    // 8. Provider Cab Services CRUD (Phase 18B)
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ProviderCabServiceResponse> getCabs() {
        ProviderEntity provider = requireCurrentProvider();
        List<CabServiceEntity> list = cabRepo.findByProviderIdOrderByCreatedAtDesc(provider.getId());
        return list.stream().map(c -> toCabResponse(c, provider.getBusinessName())).collect(Collectors.toList());
    }

    @Transactional
    public ProviderCabServiceResponse createCab(ProviderCabServiceRequest req) {
        ProviderEntity provider = requireCurrentProvider();
        checkRateLimit(provider.getId());

        if (provider.getBusinessType() != BusinessType.TRANSPORT && provider.getBusinessType() != BusinessType.HOTEL && provider.getBusinessType() != BusinessType.BOTH) {
            throw new IllegalArgumentException("Your business type (" + provider.getBusinessType() + ") does not permit cab service registrations");
        }

        if (cabRepo.countByProviderId(provider.getId()) >= props.getMaxCabServices()) {
            throw new IllegalArgumentException("Maximum of " + props.getMaxCabServices() + " cab services reached");
        }

        CityInfo city = InventoryService.findCity(req.getCity());
        if (city == null) {
            throw new IllegalArgumentException("City not found in supported catalogue: " + req.getCity());
        }

        if (req.getBaseFare().compareTo(props.getCabMinBaseFare()) < 0 || req.getBaseFare().compareTo(props.getCabMaxBaseFare()) > 0) {
            throw new IllegalArgumentException("Base fare must be between ₹" + props.getCabMinBaseFare() + " and ₹" + props.getCabMaxBaseFare());
        }
        if (req.getPerKmRate().compareTo(props.getCabMinPerKmRate()) < 0 || req.getPerKmRate().compareTo(props.getCabMaxPerKmRate()) > 0) {
            throw new IllegalArgumentException("Per km rate must be between ₹" + props.getCabMinPerKmRate() + " and ₹" + props.getCabMaxPerKmRate());
        }

        String cabId = IdGenerator.generate("CAB");
        CabServiceEntity cab = CabServiceEntity.builder()
                .id(cabId)
                .providerId(provider.getId())
                .provider(provider.getBusinessName())
                .city(city.getKey())
                .baseFare(req.getBaseFare())
                .perKm(req.getPerKmRate())
                .vehicleCategory(req.getVehicleCategory())
                .seats(req.getSeats())
                .operatingHours(req.getOperatingHours() != null ? req.getOperatingHours().trim() : "06:00 - 23:00")
                .vehicleNumber(req.getVehicleNumber() != null && !req.getVehicleNumber().isBlank() ? req.getVehicleNumber().trim() : null)
                .active(req.getActive() != null ? req.getActive() : true)
                .source("PROVIDER")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        CabServiceEntity saved = cabRepo.save(cab);
        recordAudit("PROVIDER_CAB_CREATED", "Registered " + saved.getVehicleCategory() + " cab service in " + saved.getCity());
        return toCabResponse(saved, provider.getBusinessName());
    }

    @Transactional
    public ProviderCabServiceResponse updateCab(String id, ProviderCabServiceRequest req) {
        ProviderEntity provider = requireCurrentProvider();
        checkRateLimit(provider.getId());

        CabServiceEntity cab = cabRepo.findByIdAndProviderId(id, provider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cab service not found or not owned by you: " + id));

        CityInfo city = InventoryService.findCity(req.getCity());
        if (city == null) {
            throw new IllegalArgumentException("City not found in supported catalogue: " + req.getCity());
        }

        if (req.getBaseFare().compareTo(props.getCabMinBaseFare()) < 0 || req.getBaseFare().compareTo(props.getCabMaxBaseFare()) > 0) {
            throw new IllegalArgumentException("Base fare must be between ₹" + props.getCabMinBaseFare() + " and ₹" + props.getCabMaxBaseFare());
        }
        if (req.getPerKmRate().compareTo(props.getCabMinPerKmRate()) < 0 || req.getPerKmRate().compareTo(props.getCabMaxPerKmRate()) > 0) {
            throw new IllegalArgumentException("Per km rate must be between ₹" + props.getCabMinPerKmRate() + " and ₹" + props.getCabMaxPerKmRate());
        }

        cab.setCity(city.getKey());
        cab.setBaseFare(req.getBaseFare());
        cab.setPerKm(req.getPerKmRate());
        cab.setVehicleCategory(req.getVehicleCategory());
        cab.setSeats(req.getSeats());
        cab.setOperatingHours(req.getOperatingHours() != null ? req.getOperatingHours().trim() : "06:00 - 23:00");
        cab.setVehicleNumber(req.getVehicleNumber() != null && !req.getVehicleNumber().isBlank() ? req.getVehicleNumber().trim() : null);
        if (req.getActive() != null) cab.setActive(req.getActive());
        cab.setUpdatedAt(LocalDateTime.now());

        CabServiceEntity saved = cabRepo.save(cab);
        recordAudit("PROVIDER_CAB_UPDATED", "Updated cab service " + id);
        return toCabResponse(saved, provider.getBusinessName());
    }

    @Transactional
    public void deleteCab(String id) {
        ProviderEntity provider = requireCurrentProvider();
        checkRateLimit(provider.getId());

        CabServiceEntity cab = cabRepo.findByIdAndProviderId(id, provider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cab service not found or not owned by you: " + id));

        boolean hasFutureBookings = bookingRepo.findAll().stream()
                .filter(b -> "CAB".equalsIgnoreCase(b.getType()))
                .filter(b -> (id.equals(b.getServiceId()) || cab.getProvider().equalsIgnoreCase(b.getProvider()) || (b.getProviderId() != null && b.getProviderId().equals(provider.getId()))))
                .filter(b -> b.getEndTime() != null && b.getEndTime().isAfter(LocalDateTime.now()))
                .anyMatch(b -> !"CANCELLED".equalsIgnoreCase(b.getStatus()));

        if (hasFutureBookings) {
            throw new ConflictException("Cannot delete cab service with future active bookings. You may deactivate it instead.");
        }

        cabRepo.delete(cab);
        recordAudit("PROVIDER_CAB_DELETED", "Deleted cab service " + id);
    }

    @Transactional
    public void toggleCabStatus(String id, boolean active) {
        ProviderEntity provider = requireCurrentProvider();
        CabServiceEntity cab = cabRepo.findByIdAndProviderId(id, provider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cab service not found: " + id));
        cab.setActive(active);
        cab.setUpdatedAt(LocalDateTime.now());
        cabRepo.save(cab);
    }

    // =========================================================================
    // 9. Hotel Bookings Manifest & "Cannot honour" (Phase 18B)
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ProviderHotelBookingDto> getHotelBookings(String hotelId) {
        ProviderEntity provider = requireCurrentProvider();
        assertCanManageHotels(provider);
        HotelEntity hotel = hotelRepo.findByIdAndProviderId(hotelId, provider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found or not owned by you: " + hotelId));

        List<BookingEntity> allBookings = bookingRepo.findAll();
        return allBookings.stream()
                .filter(b -> "HOTEL".equalsIgnoreCase(b.getType()))
                .filter(b -> hotelId.equals(b.getServiceId()) || hotel.getName().equalsIgnoreCase(b.getProvider()) || (b.getProviderId() != null && b.getProviderId().equals(provider.getId())))
                .sorted(Comparator.comparing(BookingEntity::getStartTime).reversed())
                .map(b -> {
                    String roomTypeName = "Standard Room";
                    if (b.getHotelRoomTypeId() != null) {
                        roomTypeName = roomTypeRepo.findById(b.getHotelRoomTypeId())
                                .map(HotelRoomTypeEntity::getName)
                                .orElse("Room");
                    }
                    String guestName = "Guest";
                    if (b.getPassengerNames() != null && !b.getPassengerNames().isBlank()) {
                        guestName = b.getPassengerNames().split("\\(")[0].trim();
                    }
                    return ProviderHotelBookingDto.builder()
                            .bookingId(b.getId())
                            .bookingReference(b.getBookingReference())
                            .guestName(guestName)
                            .checkIn(b.getStartTime() != null ? b.getStartTime().toLocalDate() : null)
                            .checkOut(b.getEndTime() != null ? b.getEndTime().toLocalDate() : null)
                            .rooms(1)
                            .roomTypeName(roomTypeName)
                            .status(b.getStatus())
                            .cannotHonour(b.getCannotHonour())
                            .cannotHonourReason(b.getCannotHonourReason())
                            .cost(b.getCost())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void markBookingCannotHonour(String bookingId, CannotHonourRequest req) {
        ProviderEntity provider = requireCurrentProvider();
        checkRateLimit(provider.getId());

        BookingEntity booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (!"HOTEL".equalsIgnoreCase(booking.getType())) {
            throw new IllegalArgumentException("Cannot honour action is only valid for hotel bookings");
        }

        // Verify provider ownership
        boolean isOwner = (booking.getProviderId() != null && booking.getProviderId().equals(provider.getId()));
        if (!isOwner) {
            isOwner = hotelRepo.findByProviderIdOrderByCreatedAtDesc(provider.getId()).stream()
                    .anyMatch(h -> h.getName().equalsIgnoreCase(booking.getProvider()));
        }
        if (!isOwner) {
            throw new AccessDeniedException("You can only report cannot-honour for your own hotel bookings");
        }

        // Flag booking without cancelling automatically
        booking.setCannotHonour(true);
        booking.setCannotHonourReason(req.getReason().trim());
        booking.setCannotHonourAt(LocalDateTime.now());
        bookingRepo.save(booking);

        // Record disruption-style alert on traveller's trip
        if (booking.getTrip() != null) {
            TripEntity trip = booking.getTrip();
            DisruptionEntity disruption = DisruptionEntity.builder()
                    .id(IdGenerator.generate("DIS"))
                    .trip(trip)
                    .bookingId(booking.getId())
                    .type("HOTEL_CANNOT_HONOUR")
                    .delayMinutes(0)
                    .reason("Provider-reported, not independently verified: Hotel cannot honour booking: " + req.getReason().trim())
                    .disruptionTime(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .source("Provider-reported, not independently verified")
                    .actor("SYSTEM")
                    .build();
            disruptionRepo.save(disruption);

            trip.setStatus("DISRUPTED");
            tripRepo.save(trip);
        }

        recordAudit("HOTEL_CANNOT_HONOUR", "Provider-reported, not independently verified: Hotel cannot honour booking "
                + booking.getBookingReference() + ": " + req.getReason().trim());
    }

    private ProviderHotelResponse toHotelResponse(HotelEntity h) {
        long count = roomTypeRepo != null ? roomTypeRepo.countByHotelId(h.getId()) : 0;
        return ProviderHotelResponse.builder()
                .id(h.getId())
                .providerId(h.getProviderId())
                .name(h.getName())
                .city(h.getCity())
                .address(h.getAddress())
                .description(h.getDescription())
                .contactEmail(h.getContactEmail())
                .checkInTime(h.getCheckInTime())
                .checkOutTime(h.getCheckOutTime())
                .rating(h.getRating())
                .active(h.getActive())
                .roomTypesCount(count)
                .createdAt(h.getCreatedAt())
                .updatedAt(h.getUpdatedAt())
                .build();
    }

    private ProviderRoomTypeResponse toRoomTypeResponse(HotelRoomTypeEntity r) {
        return ProviderRoomTypeResponse.builder()
                .id(r.getId())
                .hotelId(r.getHotelId())
                .name(r.getName())
                .maxGuests(r.getMaxGuests())
                .pricePerNight(r.getPricePerNight())
                .roomsTotal(r.getRoomsTotal())
                .amenities(r.getAmenities())
                .active(r.getActive())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private ProviderCabServiceResponse toCabResponse(CabServiceEntity c, String providerName) {
        return ProviderCabServiceResponse.builder()
                .id(c.getId())
                .providerId(c.getProviderId())
                .providerName(providerName != null ? providerName : c.getProvider())
                .city(c.getCity())
                .vehicleCategory(c.getVehicleCategory())
                .baseFare(c.getBaseFare())
                .perKmRate(c.getPerKm())
                .seats(c.getSeats())
                .operatingHours(c.getOperatingHours())
                .vehicleNumber(c.getVehicleNumber())
                .active(c.getActive())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    // =========================================================================
    // 10. Provider-Issued Refunds (Phase 18C)
    // =========================================================================

    private boolean isProviderBooking(BookingEntity booking, ProviderEntity provider) {
        if (booking.getProviderId() != null && booking.getProviderId().equals(provider.getId())) {
            return true;
        }
        if (provider.getBusinessName() != null && 
            (provider.getBusinessName().equalsIgnoreCase(booking.getProvider()) || 
             provider.getBusinessName().equalsIgnoreCase(booking.getOperator()))) {
            return true;
        }
        if ("HOTEL".equalsIgnoreCase(booking.getType())) {
            return hotelRepo.findByProviderIdOrderByCreatedAtDesc(provider.getId()).stream()
                    .anyMatch(h -> h.getName().equalsIgnoreCase(booking.getProvider()) 
                            || (booking.getServiceId() != null && booking.getServiceId().equals(h.getId())));
        }
        if ("CAB".equalsIgnoreCase(booking.getType())) {
            return cabRepo.findByProviderIdOrderByCreatedAtDesc(provider.getId()).stream()
                    .anyMatch(c -> c.getProvider().equalsIgnoreCase(booking.getProvider())
                            || (booking.getServiceId() != null && booking.getServiceId().equals(c.getId())));
        }
        if (booking.getServiceId() != null) {
            return serviceRepo.findById(booking.getServiceId())
                    .map(s -> provider.getId().equals(s.getProviderId()))
                    .orElse(false);
        }
        return false;
    }

    @Transactional
    public ProviderRefundResponse issueProviderRefund(ProviderRefundRequest req) {
        ProviderEntity provider = requireCurrentProvider();
        checkWriteRateLimit(provider.getId());

        BookingEntity booking = bookingRepo.findById(req.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + req.getBookingId()));

        if (!isProviderBooking(booking, provider)) {
            throw new AccessDeniedException("You do not have permission to refund bookings of other providers");
        }

        boolean isCancelled = "CANCELLED".equalsIgnoreCase(booking.getStatus());
        boolean isCannotHonour = Boolean.TRUE.equals(booking.getCannotHonour());
        if (!isCancelled && !isCannotHonour) {
            throw new ConflictException("Booking is neither cancelled nor marked cannot-honour");
        }

        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        }
        BigDecimal origCost = booking.getCost() != null ? booking.getCost() : BigDecimal.ZERO;
        if (req.getAmount().compareTo(origCost) > 0) {
            throw new IllegalArgumentException("Refund amount (" + req.getAmount() + ") cannot exceed original booking cost (" + origCost + ")");
        }

        if (ledgerRepo.existsByBookingIdAndBasis(booking.getId(), LedgerBasis.PROVIDER_REPORTED)) {
            throw new ConflictException("A provider refund has already been recorded for this booking");
        }

        TripEntity trip = booking.getTrip();

        TripLedgerEntryEntity entry = TripLedgerEntryEntity.builder()
                .id(IdGenerator.generate("LED"))
                .trip(trip)
                .bookingId(booking.getId())
                .type(LedgerEntryType.REFUND_RECORDED)
                .amount(req.getAmount().setScale(2, RoundingMode.HALF_UP))
                .basis(LedgerBasis.PROVIDER_REPORTED)
                .category(booking.getType() != null ? booking.getType() : "GENERAL")
                .description("Provider refund: " + (booking.getProvider() != null ? booking.getProvider() : provider.getBusinessName()))
                .note(req.getReason().trim())
                .reference("REF-" + booking.getId())
                .refundStatus("RECEIVED")
                .receivedOn(LocalDate.now())
                .createdAt(LocalDateTime.now())
                .build();
        ledgerRepo.save(entry);

        if (trip != null) {
            String ref = booking.getBookingReference() != null && !booking.getBookingReference().isBlank()
                    ? booking.getBookingReference()
                    : booking.getId();
            String amountStr = req.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
            String body = String.format("Your provider issued a refund of ₹%s for booking %s — review it in Costs & Refunds",
                    amountStr, ref);

            NotificationEntity notification = NotificationEntity.builder()
                    .id(IdGenerator.generate("NOT"))
                    .trip(trip)
                    .eventType("PROVIDER_REFUND")
                    .channel("IN_APP")
                    .recipientMasked("Traveller")
                    .subject("Provider Issued Refund")
                    .body(body)
                    .status("DELIVERED")
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepo.save(notification);
        }

        UserEntity currentUser = authService.getCurrentUserEntity();
        AuditEntryEntity audit = AuditEntryEntity.builder()
                .id(IdGenerator.generate("AUD"))
                .trip(trip)
                .bookingId(booking.getId())
                .userId(currentUser != null ? currentUser.getId() : null)
                .actor("PROVIDER")
                .action("PROVIDER_REFUND_ISSUED")
                .details(String.format("Provider %s issued refund of ₹%s for booking %s: %s",
                        provider.getBusinessName(), req.getAmount(), booking.getId(), req.getReason()))
                .timestamp(LocalDateTime.now())
                .build();
        auditRepo.save(audit);

        return ProviderRefundResponse.builder()
                .ledgerEntryId(entry.getId())
                .bookingId(booking.getId())
                .tripId(trip != null ? trip.getId() : null)
                .amount(entry.getAmount())
                .basis(entry.getBasis().name())
                .reason(entry.getNote())
                .reference(entry.getReference())
                .createdAt(entry.getCreatedAt())
                .message("Provider refund recorded successfully")
                .build();
    }

    @Transactional(readOnly = true)
    public List<ProviderRefundEligibleBookingDto> getRefundEligibleBookings() {
        ProviderEntity provider = requireCurrentProvider();
        List<BookingEntity> allBookings = bookingRepo.findAll();
        return allBookings.stream()
                .filter(b -> isProviderBooking(b, provider))
                .filter(b -> "CANCELLED".equalsIgnoreCase(b.getStatus()) || Boolean.TRUE.equals(b.getCannotHonour()))
                .sorted(Comparator.comparing(BookingEntity::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(b -> {
                    boolean refundIssued = ledgerRepo.existsByBookingIdAndBasis(b.getId(), LedgerBasis.PROVIDER_REPORTED);
                    BigDecimal alreadyRefunded = BigDecimal.ZERO;
                    if (refundIssued) {
                        alreadyRefunded = ledgerRepo.findByBookingId(b.getId()).stream()
                                .filter(e -> e.getBasis() == LedgerBasis.PROVIDER_REPORTED)
                                .map(TripLedgerEntryEntity::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                    }
                    String guestName = "Traveller";
                    if (b.getPassengerNames() != null && !b.getPassengerNames().isBlank()) {
                        guestName = b.getPassengerNames().split("\\(")[0].trim();
                    }
                    String serviceDesc = (b.getProvider() != null ? b.getProvider() : "") + " (" + b.getFromLocation() + " → " + b.getToLocation() + ")";
                    return ProviderRefundEligibleBookingDto.builder()
                            .bookingId(b.getId())
                            .bookingReference(b.getBookingReference() != null ? b.getBookingReference() : b.getId())
                            .type(b.getType())
                            .serviceDescription(serviceDesc)
                            .guestName(guestName)
                            .originalCost(b.getCost())
                            .alreadyRefunded(alreadyRefunded)
                            .status(b.getStatus())
                            .cannotHonour(b.getCannotHonour())
                            .cannotHonourReason(b.getCannotHonourReason())
                            .startTime(b.getStartTime())
                            .refundIssued(refundIssued)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 10. Provider Confirmation Workflow (Hotel/Cab)
    // =========================================================================

    @Transactional
    public List<MarketplaceBookingDto> getPendingBookings() {
        ProviderEntity provider = requireCurrentProvider();
        checkAndExpirePendingBookings();
        List<BookingEntity> pending = new ArrayList<>(bookingRepo.findByProviderIdAndStatusOrderByStartTimeDesc(
                provider.getId(), "PENDING_PROVIDER_CONFIRMATION"));
        List<BookingEntity> unassigned = bookingRepo.findByStatusOrderByStartTimeDesc("PENDING_PROVIDER_CONFIRMATION");
        for (BookingEntity b : unassigned) {
            if (b.getProviderId() == null && pending.stream().noneMatch(existing -> existing.getId().equals(b.getId()))) {
                pending.add(b);
            }
        }
        return pending.stream().map(this::toMarketBookingDto).collect(Collectors.toList());
    }

    @Transactional
    public MarketplaceBookingDto acceptBooking(String bookingId) {
        return acceptBooking(bookingId, null);
    }

    @Transactional
    public MarketplaceBookingDto acceptBooking(String bookingId, String assignedDriverName) {
        ProviderEntity provider = requireCurrentProvider();
        checkWriteRateLimit(provider.getId());

        BookingEntity b = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (b.getProviderId() != null && !provider.getId().equals(b.getProviderId())) {
            throw new AccessDeniedException("Cannot accept another provider's booking");
        }
        if (b.getProviderId() == null) {
            b.setProviderId(provider.getId());
        }

        if (!"PENDING_PROVIDER_CONFIRMATION".equalsIgnoreCase(b.getStatus())) {
            throw new IllegalStateException("Booking is not in pending confirmation state (current status: " + b.getStatus() + ")");
        }

        if (b.getConfirmationExpiresAt() != null && LocalDateTime.now().isAfter(b.getConfirmationExpiresAt())) {
            expireSingleBooking(b);
            throw new IllegalStateException("Confirmation window has expired for this booking");
        }

        b.setStatus("CONFIRMED");
        b.setProviderConfirmationStatus("ACCEPTED");
        b.setPaymentStatus("DEMO_PAID");
        if (assignedDriverName != null && !assignedDriverName.isBlank()) {
            b.setAssignedDriverName(assignedDriverName.trim());
        }
        BookingEntity saved = bookingRepo.save(b);

        recordAudit(AuditActor.USER, "PROVIDER_ACCEPTED_BOOKING",
                String.format("Provider %s accepted booking %s (%s)",
                        provider.getBusinessName(), saved.getBookingReference(), saved.getType()));

        if (saved.getTrip() != null && notificationRepo != null && saved.getUser() != null && saved.getUser().getEmail() != null) {
            notificationRepo.save(NotificationEntity.builder()
                    .id(IdGenerator.generate("NTF"))
                    .trip(saved.getTrip())
                    .eventType("BOOKING_ACCEPTED")
                    .channel("EMAIL")
                    .recipientMasked(saved.getUser().getEmail())
                    .subject("Booking Request Accepted!")
                    .body(String.format("Great news! Provider %s has confirmed your %s booking (%s).",
                            provider.getBusinessName(), saved.getType(), saved.getBookingReference()))
                    .status("SENT")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        return toMarketBookingDto(saved);
    }

    @Transactional
    public MarketplaceBookingDto rejectBooking(String bookingId, String reason) {
        ProviderEntity provider = requireCurrentProvider();
        checkWriteRateLimit(provider.getId());

        BookingEntity b = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (b.getProviderId() != null && !provider.getId().equals(b.getProviderId())) {
            throw new AccessDeniedException("Cannot reject another provider's booking");
        }
        if (b.getProviderId() == null) {
            b.setProviderId(provider.getId());
        }

        if (!"PENDING_PROVIDER_CONFIRMATION".equalsIgnoreCase(b.getStatus())) {
            throw new IllegalStateException("Booking is not in pending confirmation state (current status: " + b.getStatus() + ")");
        }

        b.setStatus("CANCELLED");
        b.setProviderConfirmationStatus("REJECTED");
        b.setCannotHonour(true);
        b.setCannotHonourReason(reason != null && !reason.isBlank() ? reason.trim() : "Declined by provider");
        b.setCannotHonourAt(LocalDateTime.now());
        BookingEntity saved = bookingRepo.save(b);

        releaseHotelRoomIfApplicable(saved);

        recordAudit(AuditActor.USER, "PROVIDER_REJECTED_BOOKING",
                String.format("Provider %s rejected booking %s (%s). Reason: %s",
                        provider.getBusinessName(), saved.getBookingReference(), saved.getType(), reason));

        if (saved.getTrip() != null && notificationRepo != null && saved.getUser() != null && saved.getUser().getEmail() != null) {
            notificationRepo.save(NotificationEntity.builder()
                    .id(IdGenerator.generate("NTF"))
                    .trip(saved.getTrip())
                    .eventType("BOOKING_REJECTED")
                    .channel("EMAIL")
                    .recipientMasked(saved.getUser().getEmail())
                    .subject("Booking Request Declined")
                    .body(String.format("Provider %s was unable to accept your booking request (%s). Please select an alternative option.",
                            provider.getBusinessName(), saved.getBookingReference()))
                    .status("SENT")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        return toMarketBookingDto(saved);
    }

    @Transactional
    public void checkAndExpirePendingBookings() {
        List<BookingEntity> expired = bookingRepo.findByStatusAndConfirmationExpiresAtBefore(
                "PENDING_PROVIDER_CONFIRMATION", LocalDateTime.now());
        for (BookingEntity b : expired) {
            expireSingleBooking(b);
        }
    }

    private void expireSingleBooking(BookingEntity b) {
        b.setStatus("CANCELLED");
        b.setProviderConfirmationStatus("EXPIRED");
        b.setCannotHonour(true);
        b.setCannotHonourReason("Provider confirmation window expired");
        b.setCannotHonourAt(LocalDateTime.now());
        bookingRepo.save(b);

        releaseHotelRoomIfApplicable(b);

        recordAudit(AuditActor.SYSTEM, "PROVIDER_CONFIRMATION_TIMEOUT",
                String.format("Confirmation window expired for booking %s (%s). Auto-cancelled.",
                        b.getBookingReference(), b.getType()));

        if (b.getTrip() != null && notificationRepo != null && b.getUser() != null && b.getUser().getEmail() != null) {
            notificationRepo.save(NotificationEntity.builder()
                    .id(IdGenerator.generate("NTF"))
                    .trip(b.getTrip())
                    .eventType("BOOKING_EXPIRED")
                    .channel("EMAIL")
                    .recipientMasked(b.getUser().getEmail())
                    .subject("Booking Request Timed Out")
                    .body(String.format("Provider response window expired for booking %s (%s). Please choose another option.",
                            b.getBookingReference(), b.getType()))
                    .status("SENT")
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }

    private void releaseHotelRoomIfApplicable(BookingEntity b) {
        if ("HOTEL".equalsIgnoreCase(b.getType()) && b.getHotelRoomTypeId() != null && b.getStartTime() != null && b.getEndTime() != null) {
            try {
                LocalDate start = b.getStartTime().toLocalDate();
                LocalDate end = b.getEndTime().toLocalDate();
                for (LocalDate d = start; d.isBefore(end); d = d.plusDays(1)) {
                    availabilityRepo.findByRoomTypeIdAndStayDate(b.getHotelRoomTypeId(), d).ifPresent(avail -> {
                        avail.setRoomsBooked(Math.max(0, avail.getRoomsBooked() - 1));
                        availabilityRepo.save(avail);
                    });
                }
            } catch (Exception ex) {
                log.warn("Failed to release hotel room availability for booking {}: {}", b.getId(), ex.getMessage());
            }
        }
    }

    private MarketplaceBookingDto toMarketBookingDto(BookingEntity b) {
        String provBiz = null;
        String provPhone = null;
        String cabContact = null;
        if (b.getProviderId() != null && providerRepo != null) {
            try {
                ProviderEntity prov = providerRepo.findById(b.getProviderId()).orElse(null);
                if (prov != null) {
                    provBiz = prov.getBusinessName();
                    provPhone = prov.getContactPhone();
                    cabContact = "Cab service contact: " + provBiz + " — " + provPhone;
                }
            } catch (Exception ignored) {
            }
        }

        return MarketplaceBookingDto.builder()
                .id(b.getId())
                .bookingReference(b.getBookingReference() != null ? b.getBookingReference() : b.getId())
                .type(b.getType())
                .provider(b.getProvider())
                .operator(b.getOperator() != null ? b.getOperator() : b.getProvider())
                .vehicleNumber(b.getVehicleNumber())
                .from(b.getFromLocation())
                .to(b.getToLocation())
                .startTime(b.getStartTime())
                .endTime(b.getEndTime())
                .cost(b.getCost())
                .status(b.getStatus())
                .paymentStatus(b.getPaymentStatus())
                .paymentMethod(b.getPaymentMethod())
                .source(b.getSource())
                .passengerNames(b.getPassengerNames())
                .tripId(b.getTrip() != null ? b.getTrip().getId() : null)
                .tripTitle(b.getTrip() != null ? b.getTrip().getTitle() : null)
                .hotelContactEmail(b.getHotelContactEmail())
                .cannotHonour(b.getCannotHonour())
                .cannotHonourReason(b.getCannotHonourReason())
                .confirmationExpiresAt(b.getConfirmationExpiresAt())
                .providerConfirmationStatus(b.getProviderConfirmationStatus())
                .providerBusinessName(provBiz)
                .providerContactPhone(provPhone)
                .assignedDriverName(b.getAssignedDriverName())
                .cabServiceContact(cabContact)
                .cancelledAt(b.getCancelledAt())
                .cancelledBy(b.getCancelledBy())
                .build();
    }
}
