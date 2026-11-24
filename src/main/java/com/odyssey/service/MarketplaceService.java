package com.odyssey.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odyssey.config.MarketGeneratedProperties;
import com.odyssey.dto.market.*;
import com.odyssey.entity.*;
import com.odyssey.exception.ConflictException;
import com.odyssey.exception.ResourceNotFoundException;
import com.odyssey.model.CityInfo;
import com.odyssey.repository.jpa.*;
import com.odyssey.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.odyssey.model.CostBreakdown;
import com.odyssey.model.CostBreakdownLine;
import com.odyssey.model.enums.BookingType;
import com.odyssey.model.enums.ProviderStatus;
import com.odyssey.model.enums.VehicleCategory;
import com.odyssey.service.status.StatusCheckService;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceService {

    public static final String DEMO_DISCLAIMER = "Marketplace bookings are simulated for this project — no real ticket is issued, no real payment is taken.";
    public static final String DECLINED_CARD_NUMBER = "4000000000000002";

    private final OperatorJpaRepository operatorRepo;
    private final ServiceJpaRepository serviceRepo;
    private final ServiceDepartureJpaRepository departureRepo;
    private final SeatHoldJpaRepository seatHoldRepo;
    private final HotelJpaRepository hotelRepo;
    private final CabServiceJpaRepository cabRepo;
    private final BookingJpaRepository bookingRepo;
    private final TripJpaRepository tripRepo;
    private final AuditEntryJpaRepository auditRepo;
    private final ObjectMapper objectMapper;
    private final MarketGeneratedProperties marketProperties;
    private final InventoryService inventoryService;
    private final com.odyssey.repository.jpa.ProviderJpaRepository providerRepo;
    private final com.odyssey.repository.jpa.HotelRoomTypeJpaRepository roomTypeRepo;
    private final com.odyssey.repository.jpa.HotelRoomAvailabilityJpaRepository availabilityRepo;

    @Value("${market.hold-minutes:10}")
    private int holdMinutes;

    @Value("${provider.booking-confirmation.timeout-seconds:30}")
    private int confirmationTimeoutSeconds;

    @Autowired(required = false)
    private StatusCheckService statusCheckService;

    @Autowired(required = false)
    private NotificationService notificationService;

    @Autowired(required = false)
    private com.odyssey.repository.jpa.NotificationJpaRepository notificationRepo;

    @Autowired(required = false)
    private com.odyssey.mapper.TripEntityMapper tripEntityMapper;

    @Autowired(required = false)
    private com.odyssey.service.status.SimulatedStatusProvider simulatedStatusProvider;

    @Autowired(required = false)
    private LedgerService ledgerService;

    @Autowired(required = false)
    private com.odyssey.config.CancellationPolicyProperties cancellationPolicyProperties;

    @Autowired(required = false)
    private com.odyssey.repository.jpa.TripLedgerEntryJpaRepository tripLedgerEntryJpaRepository;

    @Autowired(required = false)
    private ProviderVehicleJpaRepository vehicleRepo;

    private static final String REF_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private final ConcurrentHashMap<String, Object> operatorLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> routeLocks = new ConcurrentHashMap<>();

    private Object getRouteLock(String k1, String k2) {
        String key = k1.compareTo(k2) <= 0 ? k1 + "<->" + k2 : k2 + "<->" + k1;
        return routeLocks.computeIfAbsent(key, k -> new Object());
    }

    private OperatorEntity getOrCreateOperator(String id, String name, String mode, String contact) {
        return operatorRepo.findById(id).orElseGet(() -> {
            synchronized (operatorLocks.computeIfAbsent(id, k -> new Object())) {
                return operatorRepo.findById(id).orElseGet(() -> {
                    OperatorEntity op = OperatorEntity.builder()
                            .id(id)
                            .name(name)
                            .mode(mode)
                            .contact(contact)
                            .build();
                    try {
                        return operatorRepo.save(op);
                    } catch (Exception e) {
                        return operatorRepo.findById(id).orElse(op);
                    }
                });
            }
        });
    }

    /**
     * Search available transport services across city pairs and dates.
     */
    public List<ServiceSearchResponse> searchServices(String origin, String destination, LocalDate date, String mode) {
        if (origin == null || origin.isBlank() || destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Origin and destination cities are required");
        }

        CityInfo c1 = InventoryService.findCity(origin);
        CityInfo c2 = InventoryService.findCity(destination);
        if (c1 == null) {
            throw new IllegalArgumentException("Unknown origin city: " + origin);
        }
        if (c2 == null) {
            throw new IllegalArgumentException("Unknown destination city: " + destination);
        }
        if (c1.getKey().equalsIgnoreCase(c2.getKey())) {
            throw new IllegalArgumentException("Origin and destination cannot be the same");
        }

        LocalDate today = LocalDate.now();
        if (date != null && date.isBefore(today)) {
            throw new IllegalArgumentException("Search date cannot be in the past");
        }
        if (date != null && date.isAfter(today.plusDays(60))) {
            throw new IllegalArgumentException("Search date cannot be more than 60 days in advance");
        }

        LocalDate searchDate = date != null ? date : today;

        List<ServiceDepartureEntity> departures;
        synchronized (getRouteLock(c1.getKey(), c2.getKey())) {
            ensureServicesAndDepartures(c1, c2, searchDate);

            String normOrigin = c1.getKey();
            String normDest = c2.getKey();

            if (mode != null && !mode.isBlank() && !"ALL".equalsIgnoreCase(mode.trim())) {
                departures = departureRepo.findDeparturesWithMode(normOrigin, normDest, mode.trim().toUpperCase(),
                        searchDate);
                if (departures.isEmpty()) {
                    departures = departureRepo.findDeparturesWithMode(c1.getName(), c2.getName(),
                            mode.trim().toUpperCase(), searchDate);
                }
            } else {
                departures = departureRepo.findDepartures(normOrigin, normDest, searchDate);
                if (departures.isEmpty()) {
                    departures = departureRepo.findDepartures(c1.getName(), c2.getName(), searchDate);
                }
            }
        }

        LocalTime now = LocalTime.now();

        return departures.stream()
                .map(dep -> {
                    ServiceEntity s = dep.getService();
                    if (s == null)
                        return null;

                    boolean isProvider = "PROVIDER".equalsIgnoreCase(s.getSource())
                            || "PROVIDER".equalsIgnoreCase(dep.getSource());
                    com.odyssey.entity.ProviderEntity prov = null;
                    if (isProvider) {
                        String provId = dep.getProviderId() != null ? dep.getProviderId() : s.getProviderId();
                        if (provId == null || providerRepo == null) {
                            return null;
                        }
                        prov = providerRepo.findById(provId).orElse(null);
                        if (prov == null || prov.getStatus() == com.odyssey.model.enums.ProviderStatus.SUSPENDED || prov.getStatus() == com.odyssey.model.enums.ProviderStatus.REJECTED) {
                            return null; // Hidden from search when SUSPENDED or REJECTED
                        }
                    }

                    LocalDate depDate = dep.getTravelDate();
                    LocalTime depTime = s.getDepartureTime();
                    LocalDate arrDate = Boolean.TRUE.equals(s.getArrivesNextDay()) ? depDate.plusDays(1) : depDate;
                    LocalTime arrTime = s.getArrivalTime();

                    LocalDateTime depDateTime = depDate.atTime(depTime);
                    LocalDateTime arrDateTime = arrDate.atTime(arrTime);
                    long minutes = Duration.between(depDateTime, arrDateTime).toMinutes();
                    String durationStr = String.format("%dh %02dm", minutes / 60, minutes % 60);

                    boolean isPast = depDate.isBefore(today) || (depDate.isEqual(today) && depTime.isBefore(now));
                    boolean isBookable = !isPast && "SCHEDULED".equalsIgnoreCase(dep.getStatus())
                            && dep.getSeatsAvailable() > 0;

                    boolean isGenerated = "GENERATED".equalsIgnoreCase(s.getSource());
                    String scheduleLabel;
                    String sourceLabel;
                    if (isProvider) {
                        sourceLabel = "Provider-listed";
                        scheduleLabel = "Odyssey does not verify schedules or availability.";
                    } else if (isGenerated) {
                        sourceLabel = "Generated schedule";
                        scheduleLabel = "Generated schedule, not an official timetable";
                    } else {
                        sourceLabel = "Seeded schedule";
                        scheduleLabel = "Official schedule";
                    }

                    return ServiceSearchResponse.builder()
                            .id(s.getId())
                            .departureId(dep.getId())
                            .operator(s.getOperator() != null ? s.getOperator().getName() : "Odyssey Partner")
                            .vehicleNumber(s.getVehicleNumber())
                            .mode(s.getMode())
                            .originCity(s.getOriginCity())
                            .destinationCity(s.getDestinationCity())
                            .originStop(s.getOriginStop())
                            .destinationStop(s.getDestinationStop())
                            .departureDate(depDate)
                            .departureTime(depTime)
                            .arrivalDate(arrDate)
                            .arrivalTime(arrTime)
                            .duration(durationStr)
                            .price(s.getPrice())
                            .seatsLeft(dep.getSeatsAvailable())
                            .reliabilityScore(s.getReliabilityScore())
                            .status(dep.getStatus())
                            .bookable(isBookable)
                            .source(s.getSource())
                            .sourceLabel(sourceLabel)
                            .scheduleLabel(scheduleLabel)
                            .providerBusinessName(prov != null ? prov.getBusinessName() : (s.getOperator() != null ? s.getOperator().getName() : null))
                            .disclaimer(DEMO_DISCLAIMER)
                            .build();
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ServiceSearchResponse::getDepartureTime))
                .collect(Collectors.toList());
    }

    @Transactional
    public void ensureServicesAndDepartures(CityInfo c1, CityInfo c2, LocalDate searchDate) {
        ensureServicesAndDeparturesForDates(c1, c2, searchDate, searchDate.plusDays(14));
    }

    @Transactional
    public void ensureServicesAndDeparturesForDates(CityInfo c1, CityInfo c2, LocalDate startDate, LocalDate endDate) {
        synchronized (getRouteLock(c1.getKey(), c2.getKey())) {
            String normOrigin = c1.getKey();
            String normDest = c2.getKey();
            boolean exists = serviceRepo.existsByOriginCityAndDestinationCity(normOrigin, normDest)
                    || serviceRepo.existsByOriginCityAndDestinationCity(c1.getName(), c2.getName());

            if (!exists) {
                materializeGeneratedServices(c1, c2);
            }

            List<ServiceEntity> services = serviceRepo.findByOriginCityAndDestinationCityAndActiveTrue(normOrigin,
                    normDest);
            if (services.isEmpty()) {
                services = serviceRepo.findByOriginCityAndDestinationCityAndActiveTrue(c1.getName(), c2.getName());
            }

            LocalDate end = endDate != null ? endDate : startDate;
            for (ServiceEntity s : services) {
                if ("PROVIDER".equalsIgnoreCase(s.getSource())) {
                    continue; // Provider departures are managed exclusively by ProviderService
                }
                for (LocalDate d = startDate; !d.isAfter(end); d = d.plusDays(1)) {
                    if (departureRepo.findByServiceIdAndTravelDate(s.getId(), d).isPresent()) {
                        continue;
                    }
                    String depId = "DEP-" + Math.abs(Objects.hash(s.getId(), d));
                    if (!departureRepo.existsById(depId)) {
                        ServiceDepartureEntity dep = ServiceDepartureEntity.builder()
                                .id(depId)
                                .service(s)
                                .travelDate(d)
                                .seatsAvailable(s.getSeatsTotal())
                                .status("SCHEDULED")
                                .delayMinutes(0)
                                .build();
                        try {
                            departureRepo.save(dep);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
    }

    private void materializeGeneratedServices(CityInfo c1, CityInfo c2) {
        double crowDist = haversineKm(c1.getLatitude(), c1.getLongitude(), c2.getLatitude(), c2.getLongitude());
        if (crowDist < 1.0) {
            return;
        }

        // 1. BUS
        if (crowDist <= marketProperties.getBus().getMaxDistanceKm()) {
            OperatorEntity busOp = getOrCreateOperator("OP-GEN-BUS", marketProperties.getBus().getOperatorName(), "BUS",
                    "+91-1800-000-001");
            double roadDist = crowDist * marketProperties.getRoadFactor();
            long durMin = Math.max(30, Math.round((roadDist / marketProperties.getBus().getSpeedKmh()) * 60.0));
            BigDecimal baseBusPrice = BigDecimal.valueOf(
                    marketProperties.getBus().getBasePrice() + (roadDist * marketProperties.getBus().getPerKmPrice()))
                    .setScale(0, RoundingMode.HALF_UP);

            LocalTime[] busTimes = new LocalTime[] {
                    LocalTime.of(6, 0),
                    LocalTime.of(9, 30),
                    LocalTime.of(13, 0),
                    LocalTime.of(17, 30),
                    LocalTime.of(21, 0)
            };

            for (LocalTime depTime : busTimes) {
                LocalTime arrTime = depTime.plusMinutes(durMin);
                boolean arrivesNextDay = depTime.toSecondOfDay() + (durMin * 60) >= 86400;
                String vehicleNumber = "GEN-BUS-" + Math.abs(Objects.hash(c1.getKey(), c2.getKey(), depTime));
                String srvId = "SRV-GEN-BUS-" + Math.abs(Objects.hash(c1.getKey(), c2.getKey(), depTime));

                saveServiceIfAbsent(ServiceEntity.builder()
                        .id(srvId)
                        .operator(busOp)
                        .mode("BUS")
                        .vehicleNumber(vehicleNumber)
                        .originCity(c1.getKey())
                        .destinationCity(c2.getKey())
                        .originStop(c1.getName() + " Central Bus Stand")
                        .destinationStop(c2.getName() + " Bus Terminal")
                        .departureTime(depTime)
                        .arrivalTime(arrTime)
                        .arrivesNextDay(arrivesNextDay)
                        .price(baseBusPrice)
                        .seatsTotal(marketProperties.getBus().getSeats())
                        .reliabilityScore(90)
                        .active(true)
                        .source("GENERATED")
                        .build());
            }

            if (crowDist > marketProperties.getBus().getSleeperMinDistanceKm()) {
                LocalTime depTime = LocalTime.of(22, 30);
                LocalTime arrTime = depTime.plusMinutes(durMin);
                String vehicleNumber = "GEN-BUS-SLP-" + Math.abs(Objects.hash(c1.getKey(), c2.getKey(), depTime));
                String srvId = "SRV-GEN-BUS-SLP-" + Math.abs(Objects.hash(c1.getKey(), c2.getKey(), depTime));
                BigDecimal slpPrice = baseBusPrice
                        .add(BigDecimal.valueOf(marketProperties.getBus().getSleeperSurcharge()))
                        .setScale(0, RoundingMode.HALF_UP);

                saveServiceIfAbsent(ServiceEntity.builder()
                        .id(srvId)
                        .operator(busOp)
                        .mode("BUS")
                        .vehicleNumber(vehicleNumber)
                        .originCity(c1.getKey())
                        .destinationCity(c2.getKey())
                        .originStop(c1.getName() + " Central Bus Stand")
                        .destinationStop(c2.getName() + " Bus Terminal")
                        .departureTime(depTime)
                        .arrivalTime(arrTime)
                        .arrivesNextDay(true)
                        .price(slpPrice)
                        .seatsTotal(marketProperties.getBus().getSeats())
                        .reliabilityScore(88)
                        .active(true)
                        .source("GENERATED")
                        .build());
            }
        }

        // 2. TRAIN
        if (Boolean.TRUE.equals(c1.getHasRailwayStation()) && Boolean.TRUE.equals(c2.getHasRailwayStation())) {
            OperatorEntity trainOp = getOrCreateOperator("OP-GEN-TRAIN", marketProperties.getTrain().getOperatorName(),
                    "TRAIN", "+91-1800-000-002");
            double railDist = crowDist * 1.15;
            long durMin = Math.max(30, Math.round((railDist / marketProperties.getTrain().getSpeedKmh()) * 60.0));
            BigDecimal trainPrice = BigDecimal
                    .valueOf(marketProperties.getTrain().getBasePrice()
                            + (railDist * marketProperties.getTrain().getPerKmPrice()))
                    .setScale(0, RoundingMode.HALF_UP);

            LocalTime[] trainTimes = new LocalTime[] {
                    LocalTime.of(6, 30),
                    LocalTime.of(14, 0),
                    LocalTime.of(20, 30)
            };

            for (LocalTime depTime : trainTimes) {
                LocalTime arrTime = depTime.plusMinutes(durMin);
                boolean arrivesNextDay = depTime.toSecondOfDay() + (durMin * 60) >= 86400;
                String vehicleNumber = "GEN-TRN-" + Math.abs(Objects.hash(c1.getKey(), c2.getKey(), depTime));
                String srvId = "SRV-GEN-TRN-" + Math.abs(Objects.hash(c1.getKey(), c2.getKey(), depTime));

                saveServiceIfAbsent(ServiceEntity.builder()
                        .id(srvId)
                        .operator(trainOp)
                        .mode("TRAIN")
                        .vehicleNumber(vehicleNumber)
                        .originCity(c1.getKey())
                        .destinationCity(c2.getKey())
                        .originStop(c1.getName() + " Junction")
                        .destinationStop(c2.getName() + " Railway Station")
                        .departureTime(depTime)
                        .arrivalTime(arrTime)
                        .arrivesNextDay(arrivesNextDay)
                        .price(trainPrice)
                        .seatsTotal(marketProperties.getTrain().getSeats())
                        .reliabilityScore(94)
                        .active(true)
                        .source("GENERATED")
                        .build());
            }
        }

        // 3. FLIGHT
        if (Boolean.TRUE.equals(c1.getHasAirport()) && Boolean.TRUE.equals(c2.getHasAirport())
                && crowDist >= marketProperties.getFlight().getMinDistanceKm()) {
            OperatorEntity flightOp = getOrCreateOperator("OP-GEN-FLIGHT",
                    marketProperties.getFlight().getOperatorName(), "FLIGHT", "+91-1800-000-003");
            long airMin = Math.round((crowDist / marketProperties.getFlight().getSpeedKmh()) * 60.0)
                    + marketProperties.getFlight().getGroundAllowanceMinutes();
            BigDecimal flightPrice = BigDecimal
                    .valueOf(marketProperties.getFlight().getBasePrice()
                            + (crowDist * marketProperties.getFlight().getPerKmPrice()))
                    .setScale(0, RoundingMode.HALF_UP);

            LocalTime[] flightTimes = new LocalTime[] {
                    LocalTime.of(7, 15),
                    LocalTime.of(12, 45),
                    LocalTime.of(18, 30)
            };

            for (LocalTime depTime : flightTimes) {
                LocalTime arrTime = depTime.plusMinutes(airMin);
                boolean arrivesNextDay = depTime.toSecondOfDay() + (airMin * 60) >= 86400;
                String vehicleNumber = "GEN-FLT-" + Math.abs(Objects.hash(c1.getKey(), c2.getKey(), depTime));
                String srvId = "SRV-GEN-FLT-" + Math.abs(Objects.hash(c1.getKey(), c2.getKey(), depTime));

                saveServiceIfAbsent(ServiceEntity.builder()
                        .id(srvId)
                        .operator(flightOp)
                        .mode("FLIGHT")
                        .vehicleNumber(vehicleNumber)
                        .originCity(c1.getKey())
                        .destinationCity(c2.getKey())
                        .originStop(c1.getName() + " Airport ("
                                + (c1.getAirportCodes().isEmpty() ? "DOM" : c1.getAirportCodes().get(0)) + ")")
                        .destinationStop(c2.getName() + " Airport ("
                                + (c2.getAirportCodes().isEmpty() ? "DOM" : c2.getAirportCodes().get(0)) + ")")
                        .departureTime(depTime)
                        .arrivalTime(arrTime)
                        .arrivesNextDay(arrivesNextDay)
                        .price(flightPrice)
                        .seatsTotal(marketProperties.getFlight().getSeats())
                        .reliabilityScore(92)
                        .active(true)
                        .source("GENERATED")
                        .build());
            }
        }
    }

    private void saveServiceIfAbsent(ServiceEntity service) {
        if (!serviceRepo.existsById(service.getId())) {
            try {
                serviceRepo.save(service);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Search hotels by city and dates.
     */
    @Transactional
    public List<HotelSearchResponse> searchHotels(String city, LocalDate checkIn, LocalDate checkOut, int rooms) {
        if (city == null || city.isBlank()) {
            return Collections.emptyList();
        }

        CityInfo c = InventoryService.findCity(city);
        if (c != null) {
            ensureHotels(c);
        }

        String normCity = c != null ? c.getKey() : InventoryService.normalizeCityKey(city);
        List<HotelEntity> hotels = hotelRepo.findByCityIgnoreCase(normCity);
        if (hotels.isEmpty() && c != null) {
            hotels = hotelRepo.findByCityIgnoreCase(c.getName());
        }

        LocalDate inDate = checkIn != null ? checkIn : LocalDate.now();
        LocalDate outDate = checkOut != null ? checkOut : inDate.plusDays(1);
        long nights = Math.max(1, ChronoUnit.DAYS.between(inDate, outDate));
        int roomCount = Math.max(1, rooms);

        List<HotelSearchResponse> responses = new ArrayList<>();

        for (HotelEntity h : hotels) {
            if (h.getProviderId() != null) {
                // Provider-listed hotel: must be active and not suspended/rejected
                if (Boolean.FALSE.equals(h.getActive()))
                    continue;
                com.odyssey.entity.ProviderEntity prov = providerRepo.findById(h.getProviderId()).orElse(null);
                if (prov == null || prov.getStatus() == com.odyssey.model.enums.ProviderStatus.SUSPENDED || prov.getStatus() == com.odyssey.model.enums.ProviderStatus.REJECTED) {
                    continue;
                }

                List<com.odyssey.entity.HotelRoomTypeEntity> roomTypes = roomTypeRepo
                        .findByHotelIdAndActiveTrueOrderByPricePerNightAsc(h.getId());
                for (com.odyssey.entity.HotelRoomTypeEntity rt : roomTypes) {
                    // Check availability across stay dates
                    int minAvailable = rt.getRoomsTotal();
                    boolean anyClosed = false;

                    for (LocalDate d = inDate; d.isBefore(outDate); d = d.plusDays(1)) {
                        Optional<com.odyssey.entity.HotelRoomAvailabilityEntity> optAvail = availabilityRepo
                                .findByRoomTypeIdAndStayDate(rt.getId(), d);
                        if (optAvail.isPresent()) {
                            com.odyssey.entity.HotelRoomAvailabilityEntity avail = optAvail.get();
                            if (Boolean.TRUE.equals(avail.getIsClosed())) {
                                anyClosed = true;
                                minAvailable = 0;
                                break;
                            }
                            int left = avail.getRoomsTotal() - avail.getRoomsBooked();
                            if (left < minAvailable)
                                minAvailable = Math.max(0, left);
                        }
                    }

                    int effectiveAvailable = anyClosed ? 0 : Math.max(0, minAvailable);

                    BigDecimal total = rt.getPricePerNight().multiply(BigDecimal.valueOf(nights * roomCount));

                    responses.add(HotelSearchResponse.builder()
                            .id(h.getId())
                            .name(h.getName() + " - " + rt.getName())
                            .city(h.getCity())
                            .address(h.getAddress())
                            .pricePerNight(rt.getPricePerNight())
                            .rating(null) // Provider listings show "Not rated"
                            .roomsTotal(rt.getRoomsTotal())
                            .roomsAvailable(effectiveAvailable)
                            .contactEmail(null) // Shown ONLY after booking
                            .nights((int) nights)
                            .rooms(roomCount)
                            .estimatedTotal(total)
                            .source("PROVIDER")
                            .listingLabel("Provider-listed • " + prov.getBusinessName())
                            .disclaimer("Odyssey does not verify listings or availability.")
                            .roomTypeId(rt.getId())
                            .roomTypeName(rt.getName())
                            .maxGuests(rt.getMaxGuests())
                            .amenities(rt.getAmenities())
                            .providerName(prov.getBusinessName())
                            .build());
                }
            } else {
                // Seeded or Generated hotel
                BigDecimal total = h.getPricePerNight().multiply(BigDecimal.valueOf(nights * roomCount));
                boolean isGen = "GENERATED".equalsIgnoreCase(h.getSource());
                responses.add(HotelSearchResponse.builder()
                        .id(h.getId())
                        .name(h.getName())
                        .city(h.getCity())
                        .address(h.getAddress())
                        .pricePerNight(h.getPricePerNight())
                        .rating(h.getRating())
                        .roomsTotal(h.getRoomsTotal() != null ? h.getRoomsTotal() : 10)
                        .roomsAvailable(h.getRoomsTotal() != null ? h.getRoomsTotal() : 10)
                        .contactEmail(null) // Shown ONLY after booking
                        .nights((int) nights)
                        .rooms(roomCount)
                        .estimatedTotal(total)
                        .source(h.getSource())
                        .listingLabel(isGen ? "Generated listing" : "Seeded listing")
                        .disclaimer(DEMO_DISCLAIMER)
                        .roomTypeId("RT-DEF-" + h.getId())
                        .roomTypeName("Standard Room")
                        .maxGuests(2)
                        .amenities("Standard Amenities")
                        .providerName(null)
                        .build());
            }
        }

        // Sort: options with available rooms at top (by price), fully booked/zero-availability at bottom
        responses.sort(Comparator
                .comparing((HotelSearchResponse r) -> (r.getRoomsAvailable() != null && r.getRoomsAvailable() >= roomCount) ? 0 : 1)
                .thenComparing(r -> r.getPricePerNight() != null ? r.getPricePerNight() : BigDecimal.ZERO));

        return responses;
    }

    @Transactional
    public void ensureHotels(CityInfo city) {
        if (!hotelRepo.findByCityIgnoreCase(city.getKey()).isEmpty()
                || !hotelRepo.findByCityIgnoreCase(city.getName()).isEmpty()) {
            return;
        }

        String tier = city.getTier() != null ? city.getTier().toUpperCase() : "MEDIUM";
        BigDecimal p1, p2, p3;
        switch (tier) {
            case "METRO":
                p1 = new BigDecimal("3500.00");
                p2 = new BigDecimal("2200.00");
                p3 = new BigDecimal("4500.00");
                break;
            case "LARGE":
                p1 = new BigDecimal("2500.00");
                p2 = new BigDecimal("1600.00");
                p3 = new BigDecimal("3200.00");
                break;
            case "SMALL":
                p1 = new BigDecimal("1400.00");
                p2 = new BigDecimal("950.00");
                p3 = new BigDecimal("1900.00");
                break;
            case "MEDIUM":
            default:
                p1 = new BigDecimal("1800.00");
                p2 = new BigDecimal("1200.00");
                p3 = new BigDecimal("2400.00");
                break;
        }

        saveHotelIfAbsent(HotelEntity.builder()
                .id("HTL-GEN-" + city.getKey() + "-1")
                .name(city.getName() + " Grand Stay")
                .city(city.getKey())
                .address("Station Road, " + city.getName())
                .pricePerNight(p1)
                .rating(new BigDecimal("4.5"))
                .roomsTotal(30)
                .contactEmail("stay@" + city.getKey().toLowerCase() + "grand.com")
                .source("GENERATED")
                .build());

        saveHotelIfAbsent(HotelEntity.builder()
                .id("HTL-GEN-" + city.getKey() + "-2")
                .name(city.getName() + " Comfort Residency")
                .city(city.getKey())
                .address("Main Market, " + city.getName())
                .pricePerNight(p2)
                .rating(new BigDecimal("4.1"))
                .roomsTotal(25)
                .contactEmail("contact@" + city.getKey().toLowerCase() + "comfort.com")
                .source("GENERATED")
                .build());

        saveHotelIfAbsent(HotelEntity.builder()
                .id("HTL-GEN-" + city.getKey() + "-3")
                .name(city.getName() + " Heritage Suites")
                .city(city.getKey())
                .address("Civil Lines, " + city.getName())
                .pricePerNight(p3)
                .rating(new BigDecimal("4.7"))
                .roomsTotal(20)
                .contactEmail("info@" + city.getKey().toLowerCase() + "heritage.com")
                .source("GENERATED")
                .build());
    }

    private void saveHotelIfAbsent(HotelEntity hotel) {
        try {
            hotelRepo.saveAndFlush(hotel);
        } catch (Exception ignored) {
        }
    }

    /**
     * Get estimated cab quotes for a pickup/dropoff route.
     */
    @Transactional
    public List<CabQuoteResponse> getCabQuotes(String city, String pickup, String dropoff) {
        if (city == null || city.isBlank()) {
            return Collections.emptyList();
        }

        CityInfo c = InventoryService.findCity(city);
        if (c == null && pickup != null && !pickup.isBlank()) {
            c = InventoryService.findCity(pickup);
        }
        if (c == null && dropoff != null && !dropoff.isBlank()) {
            c = InventoryService.findCity(dropoff);
        }
        if (c != null) {
            ensureCabs(c);
        }

        String normCity = c != null ? c.getKey() : InventoryService.normalizeCityKey(city);
        syncProviderCabVehiclesToCabRepo(normCity, c != null ? c.getName() : city);

        List<CabServiceEntity> cabs = new ArrayList<>(cabRepo.findByCityIgnoreCase(normCity));
        if (c != null && !c.getName().equalsIgnoreCase(normCity)) {
            for (CabServiceEntity cs : cabRepo.findByCityIgnoreCase(c.getName())) {
                if (cabs.stream().noneMatch(x -> x.getId().equals(cs.getId()))) {
                    cabs.add(cs);
                }
            }
        }
        if (city != null && !city.equalsIgnoreCase(normCity) && (c == null || !city.equalsIgnoreCase(c.getName()))) {
            for (CabServiceEntity cs : cabRepo.findByCityIgnoreCase(city)) {
                if (cabs.stream().noneMatch(x -> x.getId().equals(cs.getId()))) {
                    cabs.add(cs);
                }
            }
        }

        double distanceKm = estimateCabDistance(pickup, dropoff, normCity);

        return cabs.stream()
                .filter(cab -> {
                    if (cab.getProviderId() != null) {
                        if (Boolean.FALSE.equals(cab.getActive()))
                            return false;
                        com.odyssey.entity.ProviderEntity prov = providerRepo.findById(cab.getProviderId())
                                .orElse(null);
                        return prov != null && prov.getStatus() != com.odyssey.model.enums.ProviderStatus.SUSPENDED && prov.getStatus() != com.odyssey.model.enums.ProviderStatus.REJECTED;
                    }
                    return true;
                })
                .map(cab -> {
                    BigDecimal perKmCost = cab.getPerKm().multiply(BigDecimal.valueOf(distanceKm));
                    BigDecimal estimatedFare = cab.getBaseFare().add(perKmCost).setScale(2, RoundingMode.HALF_UP);
                    boolean isProvider = cab.getProviderId() != null;
                    boolean isGen = "GENERATED".equalsIgnoreCase(cab.getSource());

                    String listingLabel;
                    String disclaimer;
                    if (isProvider) {
                        com.odyssey.entity.ProviderEntity prov = providerRepo.findById(cab.getProviderId())
                                .orElse(null);
                        listingLabel = "Provider-listed • "
                                + (prov != null ? prov.getBusinessName() : cab.getProvider());
                        disclaimer = "Odyssey does not verify listings or availability.";
                    } else {
                        listingLabel = isGen ? "Generated listing" : "Seeded listing";
                        disclaimer = DEMO_DISCLAIMER;
                    }

                    return CabQuoteResponse.builder()
                            .id(cab.getId())
                            .provider(cab.getProvider())
                            .city(cab.getCity())
                            .baseFare(cab.getBaseFare())
                            .perKm(cab.getPerKm())
                            .estimatedDistanceKm(distanceKm)
                            .estimatedFare(estimatedFare)
                            .estimated(true)
                            .source(isProvider ? "PROVIDER" : cab.getSource())
                            .listingLabel(listingLabel)
                            .disclaimer(disclaimer)
                            .vehicleCategory(
                                    cab.getVehicleCategory() != null ? cab.getVehicleCategory().name() : "SEDAN")
                            .seats(cab.getSeats() != null ? cab.getSeats() : 4)
                            .operatingHours(cab.getOperatingHours())
                            .build();
                })
                .sorted(Comparator
                        .comparing((CabQuoteResponse cabQuote) -> (cabQuote.getSeats() != null && cabQuote.getSeats() > 0) ? 0 : 1)
                        .thenComparing(cabQuote -> cabQuote.getEstimatedFare() != null ? cabQuote.getEstimatedFare() : BigDecimal.ZERO))
                .collect(Collectors.toList());
    }

    private void syncProviderCabVehiclesToCabRepo(String normCity, String cityName) {
        if (vehicleRepo == null || providerRepo == null || cabRepo == null) {
            return;
        }
        try {
            List<ProviderEntity> providers = providerRepo.findAll().stream()
                    .filter(p -> {
                        if (p.getStatus() == ProviderStatus.SUSPENDED || p.getStatus() == ProviderStatus.REJECTED) return false;
                        if (p.getCity() == null) return false;
                        CityInfo pi = InventoryService.findCity(p.getCity());
                        if (pi != null) {
                            return pi.getKey().equalsIgnoreCase(normCity) || (cityName != null && pi.getName().equalsIgnoreCase(cityName));
                        }
                        return p.getCity().equalsIgnoreCase(normCity) || (cityName != null && p.getCity().equalsIgnoreCase(cityName));
                    })
                    .collect(Collectors.toList());

            for (ProviderEntity prov : providers) {
                List<ProviderVehicleEntity> vehicles = vehicleRepo.findByProviderIdOrderByCreatedAtDesc(prov.getId());
                for (ProviderVehicleEntity v : vehicles) {
                    if (v.getMode() == BookingType.CAB && Boolean.TRUE.equals(v.getActive())) {
                        List<CabServiceEntity> existing = cabRepo.findByProviderIdOrderByCreatedAtDesc(prov.getId());
                        boolean exists = existing.stream().anyMatch(cs -> v.getVehicleNumber() != null && v.getVehicleNumber().equalsIgnoreCase(cs.getVehicleNumber()));
                        if (!exists) {
                            CabServiceEntity cab = CabServiceEntity.builder()
                                    .id(IdGenerator.generate("CAB-"))
                                    .provider(prov.getBusinessName() != null ? prov.getBusinessName() : "Local Cab")
                                    .city(cityName != null ? cityName : normCity)
                                    .baseFare(new BigDecimal("50.00"))
                                    .perKm(new BigDecimal("14.00"))
                                    .source("PROVIDER")
                                    .providerId(prov.getId())
                                    .vehicleCategory(v.getVehicleType() != null && v.getVehicleType().equalsIgnoreCase("SUV") ? VehicleCategory.SUV : ((v.getSeatsTotal() != null && v.getSeatsTotal() > 4) ? VehicleCategory.SUV : VehicleCategory.SEDAN))
                                    .seats(v.getSeatsTotal() != null ? v.getSeatsTotal() : 4)
                                    .operatingHours("24x7 On-Demand")
                                    .vehicleNumber(v.getVehicleNumber())
                                    .active(true)
                                    .createdAt(LocalDateTime.now())
                                    .updatedAt(LocalDateTime.now())
                                    .build();
                            cabRepo.save(cab);
                            log.info("Auto-synced provider cab vehicle {} to cabRepo for city {}", v.getVehicleNumber(), cityName);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Error syncing provider cab vehicles to cabRepo: {}", ex.getMessage());
        }
    }

    @Transactional
    public void ensureCabs(CityInfo city) {
        if (!cabRepo.findByCityIgnoreCase(city.getKey()).isEmpty()
                || !cabRepo.findByCityIgnoreCase(city.getName()).isEmpty()) {
            return;
        }

        saveCabIfAbsent(CabServiceEntity.builder()
                .id("CAB-GEN-" + city.getKey() + "-SEDAN")
                .city(city.getKey())
                .provider(city.getName() + " City Cabs")
                .baseFare(new BigDecimal("100.00"))
                .perKm(new BigDecimal("14.00"))
                .source("GENERATED")
                .build());

        saveCabIfAbsent(CabServiceEntity.builder()
                .id("CAB-GEN-" + city.getKey() + "-SUV")
                .city(city.getKey())
                .provider(city.getName() + " Express Transfer")
                .baseFare(new BigDecimal("160.00"))
                .perKm(new BigDecimal("19.00"))
                .source("GENERATED")
                .build());
    }

    private void saveCabIfAbsent(CabServiceEntity cab) {
        try {
            cabRepo.saveAndFlush(cab);
        } catch (Exception ignored) {
        }
    }

    public double estimateCabDistance(String pickup, String dropoff, String city) {
        String pStr = (pickup != null) ? pickup.trim() : "";
        String dStr = (dropoff != null) ? dropoff.trim() : "";
        String cStr = (city != null) ? city.trim() : "";

        CityInfo pCity = InventoryService.findCity(pStr);
        CityInfo dCity = InventoryService.findCity(dStr);
        CityInfo cCity = !cStr.isBlank() ? InventoryService.findCity(cStr) : null;

        // Effective origin and destination cities
        CityInfo originCity = (pCity != null) ? pCity : cCity;
        CityInfo destCity = (dCity != null) ? dCity : cCity;

        // 1. Inter-city route: different known cities
        if (originCity != null && destCity != null && !originCity.getKey().equalsIgnoreCase(destCity.getKey())) {
            double crowKm = InventoryService.calculateDistance(originCity, destCity);
            double roadKm = crowKm * 1.2; // 20% road routing buffer
            return Math.max(15.0, Math.round(roadKm * 10.0) / 10.0);
        }

        // 2. Intra-city route: same city or local destination transfer
        return estimateWithinCityDistance(pStr, dStr, originCity != null ? originCity : destCity);
    }

    private double estimateWithinCityDistance(String pickup, String dropoff, CityInfo city) {
        String pLower = pickup != null ? pickup.toLowerCase().trim() : "";
        String dLower = dropoff != null ? dropoff.toLowerCase().trim() : "";
        String cKey = city != null ? city.getKey().toUpperCase() : "";

        boolean pAirport = isAirportLocation(pLower);
        boolean dAirport = isAirportLocation(dLower);

        boolean pStation = isTransitHubLocation(pLower);
        boolean dStation = isTransitHubLocation(dLower);

        boolean pHill = isOutskirtsLocation(pLower);
        boolean dHill = isOutskirtsLocation(dLower);

        boolean pHotel = isCenterOrHotelLocation(pLower);
        boolean dHotel = isCenterOrHotelLocation(dLower);

        // 1. Airport routes
        if (pAirport || dAirport) {
            if (pStation || dStation || pHotel || dHotel) {
                if ("BENGALURU".equals(cKey) || "BLR".equals(cKey)) return 34.5;
                if ("MUMBAI".equals(cKey)) return 24.0;
                if ("DELHI".equals(cKey)) return 22.0;
                if ("HUBBALLI".equals(cKey)) return 11.5;
                if ("GOA".equals(cKey)) return 28.0;
                return 16.5; // Mysore / Mangaluru / tier-2 standard
            }
            if (pHill || dHill) {
                return ("BENGALURU".equals(cKey)) ? 42.0 : 25.0;
            }
            return ("BENGALURU".equals(cKey)) ? 32.0 : 18.0;
        }

        // 2. Outskirts / Hill routes (Chamundi hill, resorts, viewpoints)
        if (pHill || dHill) {
            if (pStation || dStation) {
                return 13.5;
            }
            if (pHotel || dHotel) {
                return 12.0;
            }
            return 14.0;
        }

        // 3. Transit Hub to Transit Hub (e.g. Railway Station to Bus Stand)
        if (pStation && dStation) {
            return 3.5;
        }

        // 4. Transit Hub to Hotel / Palace / City Center
        if ((pStation && dHotel) || (dStation && pHotel)) {
            return 5.8;
        }

        // 5. Hotel to Hotel / Center to Center
        if (pHotel && dHotel) {
            return 4.2;
        }

        // 6. Dynamic deterministic distance for named local points
        if (!pLower.isBlank() && !dLower.isBlank()) {
            int hash = Math.abs(Objects.hash(pLower, dLower));
            double dist = 4.5 + ((hash % 70) / 10.0);
            return Math.round(dist * 10.0) / 10.0;
        }

        return 8.0; // Standard local transfer default
    }

    private boolean isAirportLocation(String loc) {
        return loc.contains("airport") || loc.contains("aerodrome") || loc.contains("terminal 1")
                || loc.contains("terminal 2") || loc.contains("kempegowda int") || loc.contains("kia");
    }

    private boolean isTransitHubLocation(String loc) {
        return loc.contains("station") || loc.contains("railway") || loc.contains("bus stand")
                || loc.contains("bus stop") || loc.contains("terminal") || loc.contains("junction")
                || loc.contains("majestic") || loc.contains("central");
    }

    private boolean isOutskirtsLocation(String loc) {
        return loc.contains("hill") || loc.contains("chamundi") || loc.contains("resort")
                || loc.contains("outskirt") || loc.contains("lake") || loc.contains("amusement")
                || loc.contains("water park") || loc.contains("fort") || loc.contains("bypass");
    }

    private boolean isCenterOrHotelLocation(String loc) {
        return loc.contains("hotel") || loc.contains("palace") || loc.contains("center")
                || loc.contains("centre") || loc.contains("market") || loc.contains("mall")
                || loc.contains("residency") || loc.contains("inn") || loc.contains("suites")
                || loc.contains("roopa") || loc.contains("dasaprakash") || loc.contains("mercure")
                || loc.contains("radisson") || loc.contains("taj") || loc.contains("grand")
                || loc.contains("stay") || loc.contains("lodge") || loc.contains("circle");
    }

    /**
     * Book seats on a transport service in ONE pessimistic transaction.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MarketplaceBookingDto bookTransport(MarketplaceBookingRequest request, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to make a marketplace booking");
        }
        if (request.getPassengers() == null || request.getPassengers().isEmpty()) {
            throw new IllegalArgumentException("At least one passenger is required");
        }
        if (request.getPassengers().size() > 6) {
            throw new IllegalArgumentException("Maximum 6 passengers allowed per booking");
        }

        // Pessimistic write lock on departure
        ServiceDepartureEntity departure = departureRepo.findByIdForUpdate(request.getServiceDepartureId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service departure not found: " + request.getServiceDepartureId()));

        ServiceEntity service = departure.getService();

        // 1. Past departure check
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        if (departure.getTravelDate().isBefore(today) ||
                (departure.getTravelDate().isEqual(today) && service.getDepartureTime().isBefore(now))) {
            throw new IllegalArgumentException("Cannot book past departures");
        }

        // 2. Cancellation check
        if ("CANCELLED".equalsIgnoreCase(departure.getStatus())) {
            throw new ConflictException("Service departure has been cancelled");
        }

        // 3. Seats available check
        int requestedSeatsCount = request.getPassengers().size();
        if (departure.getSeatsAvailable() < requestedSeatsCount) {
            throw new ConflictException("Not enough seats available on this service departure (sold out)");
        }

        // 4. Resolve and verify seat numbers
        List<String> resolvedSeats = new ArrayList<>();
        List<SeatHoldEntity> existingDepartureHolds = seatHoldRepo.findByDepartureId(departure.getId());
        Set<String> takenSeats = existingDepartureHolds.stream()
                .map(SeatHoldEntity::getSeatNumber)
                .collect(Collectors.toSet());

        boolean hasExplicitSeats = request.getPassengers().stream()
                .anyMatch(p -> p.getSeatNumber() != null && !p.getSeatNumber().isBlank());

        if (hasExplicitSeats) {
            Set<String> seenSeatsInRequest = new HashSet<>();
            for (PassengerDto p : request.getPassengers()) {
                String seat = p.getSeatNumber() != null ? p.getSeatNumber().trim() : null;
                if (seat == null || seat.isBlank()) {
                    throw new IllegalArgumentException(
                            "All passengers must have a seat number or omit seat numbers for auto-allocation");
                }
                if (!seenSeatsInRequest.add(seat)) {
                    throw new IllegalArgumentException("Duplicate seat number in booking request: " + seat);
                }
                if (takenSeats.contains(seat)) {
                    throw new ConflictException("Seat " + seat + " is already booked");
                }
                resolvedSeats.add(seat);
            }
        } else {
            // Auto-assign seats
            int assigned = 0;
            for (int i = 1; i <= service.getSeatsTotal() && assigned < requestedSeatsCount; i++) {
                String candidateSeat = String.valueOf(i);
                if (!takenSeats.contains(candidateSeat)) {
                    resolvedSeats.add(candidateSeat);
                    assigned++;
                }
            }
            if (resolvedSeats.size() < requestedSeatsCount) {
                throw new ConflictException("Not enough contiguous free seats available to complete booking");
            }
            for (int i = 0; i < request.getPassengers().size(); i++) {
                request.getPassengers().get(i).setSeatNumber(resolvedSeats.get(i));
            }
        }

        // 5. Create Booking Entity
        String bookingId = IdGenerator.generate("BKG");
        String bookingRef = "ODY-" + generateAlphaNumeric(6);

        LocalDate depDate = departure.getTravelDate();
        LocalDate arrDate = Boolean.TRUE.equals(service.getArrivesNextDay()) ? depDate.plusDays(1) : depDate;
        LocalDateTime startTime = depDate.atTime(service.getDepartureTime());
        LocalDateTime endTime = arrDate.atTime(service.getArrivalTime());

        BigDecimal totalCost = service.getPrice().multiply(BigDecimal.valueOf(requestedSeatsCount));

        String seatNumbersJoined = String.join(", ", resolvedSeats);
        String passengerSummary = request.getPassengers().stream()
                .map(p -> p.getName() + (p.getAge() != null ? " (" + p.getAge() + ")" : ""))
                .collect(Collectors.joining(", "));

        TripEntity trip = resolveOrCreateTrip(
                request.getTripId(),
                request.getCreateTrip(),
                currentUser,
                service.getOriginCity() + " to " + service.getDestinationCity(),
                totalCost,
                endTime,
                service.getMode());

        BookingEntity booking = BookingEntity.builder()
                .id(bookingId)
                .trip(trip)
                .user(currentUser)
                .type(service.getMode())
                .provider(service.getOperator() != null ? service.getOperator().getName() : "Odyssey Transport")
                .fromLocation(service.getOriginCity() + " (" + service.getOriginStop() + ")")
                .toLocation(service.getDestinationCity() + " (" + service.getDestinationStop() + ")")
                .startTime(startTime)
                .endTime(endTime)
                .cost(totalCost)
                .status("CONFIRMED")
                .paymentStatus("DEMO_PAID")
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod().toUpperCase() : "CARD")
                .source("MARKETPLACE")
                .bookingReference(bookingRef)
                .serviceId(service.getId())
                .departureId(departure.getId())
                .providerId(service.getProviderId() != null ? service.getProviderId() : departure.getProviderId())
                .vehicleNumber(service.getVehicleNumber())
                .operator(service.getOperator() != null ? service.getOperator().getName() : "Odyssey Partner")
                .seatNumbers(seatNumbersJoined)
                .passengerNames(passengerSummary)
                .lastStatusState("ON_TIME")
                .lastDelayMinutes(0)
                .lastStatusSource("SIMULATED")
                .lastCheckedAt(LocalDateTime.now())
                .build();

        calculateDependsOnAndLinkOthers(booking, trip);

        bookingRepo.save(booking);

        // 6. Insert Seat Holds
        for (String seat : resolvedSeats) {
            SeatHoldEntity hold = SeatHoldEntity.builder()
                    .id(IdGenerator.generate("HLD"))
                    .departure(departure)
                    .seatNumber(seat)
                    .bookingId(booking.getId())
                    .status("CONFIRMED")
                    .createdAt(LocalDateTime.now())
                    .build();
            seatHoldRepo.save(hold);
        }

        // 7. Decrement available seats
        departure.setSeatsAvailable(departure.getSeatsAvailable() - requestedSeatsCount);
        departureRepo.save(departure);

        // 8. Record audit entry
        AuditEntryEntity audit = AuditEntryEntity.builder()
                .id(IdGenerator.generate("AUD"))
                .trip(trip)
                .userId(currentUser.getId())
                .bookingId(booking.getId())
                .timestamp(LocalDateTime.now())
                .actor("USER")
                .action("BOOKING_ADDED")
                .details(String.format("Marketplace booking %s added", bookingRef))
                .build();
        auditRepo.save(audit);

        // 9. Process Cab Add-on if requested alongside transport
        MarketplaceBookingDto cabAddonDto = null;
        if (request.getCabAddon() != null) {
            CabBookingRequest cabReq = request.getCabAddon();
            cabReq.setTripId(trip.getId());
            cabReq.setCreateTrip(false);
            if (cabReq.getPickupTime() == null) {
                cabReq.setPickupTime(departure.getTravelDate().atTime(service.getArrivalTime()));
            }
            if (cabReq.getPaymentMethod() == null) {
                cabReq.setPaymentMethod(booking.getPaymentMethod());
            }
            if (cabReq.getCabServiceId() == null) {
                cabRepo.findByCityIgnoreCase(service.getDestinationCity()).stream().findFirst()
                        .ifPresent(c -> cabReq.setCabServiceId(c.getId()));
                if (cabReq.getCabServiceId() == null) {
                    cabRepo.findAll().stream().findFirst()
                            .ifPresent(c -> cabReq.setCabServiceId(c.getId()));
                }
            }
            if (cabReq.getCabServiceId() != null) {
                cabAddonDto = bookCab(cabReq, currentUser);
            }
        }

        // 10. Process Hotel Add-on if requested alongside transport
        MarketplaceBookingDto hotelAddonDto = null;
        if (request.getHotelAddon() != null) {
            HotelBookingRequest hotelReq = request.getHotelAddon();
            hotelReq.setTripId(trip.getId());
            hotelReq.setCreateTrip(false);
            if (hotelReq.getCheckIn() == null) {
                hotelReq.setCheckIn(departure.getTravelDate());
            }
            if (hotelReq.getCheckOut() == null) {
                hotelReq.setCheckOut(hotelReq.getCheckIn().plusDays(1));
            }
            if (hotelReq.getRooms() <= 0) {
                hotelReq.setRooms(1);
            }
            if (hotelReq.getGuestName() == null || hotelReq.getGuestName().isBlank()) {
                String firstPax = (request.getPassengers() != null && !request.getPassengers().isEmpty())
                        ? request.getPassengers().get(0).getName() : (currentUser.getName() != null ? currentUser.getName() : "Traveller");
                hotelReq.setGuestName(firstPax);
            }
            if (hotelReq.getPaymentMethod() == null) {
                hotelReq.setPaymentMethod(booking.getPaymentMethod());
            }
            if (hotelReq.getHotelId() == null) {
                hotelRepo.findByCityIgnoreCase(service.getDestinationCity()).stream().findFirst()
                        .ifPresent(h -> hotelReq.setHotelId(h.getId()));
                if (hotelReq.getHotelId() == null) {
                    hotelRepo.findAll().stream().findFirst()
                            .ifPresent(h -> hotelReq.setHotelId(h.getId()));
                }
            }
            if (hotelReq.getHotelId() != null) {
                hotelAddonDto = bookHotel(hotelReq, currentUser);
            }
        }

        log.info("Marketplace booking confirmed: ref={}, service={}, seats={}",
                bookingRef, service.getVehicleNumber(), seatNumbersJoined);

        MarketplaceBookingDto transportDto = toDto(booking, null);
        if (cabAddonDto != null && com.odyssey.model.enums.BookingStatus.PENDING_PROVIDER_CONFIRMATION.name().equals(cabAddonDto.getStatus())) {
            transportDto.setPendingAddon(cabAddonDto);
        } else if (hotelAddonDto != null && com.odyssey.model.enums.BookingStatus.PENDING_PROVIDER_CONFIRMATION.name().equals(hotelAddonDto.getStatus())) {
            transportDto.setPendingAddon(hotelAddonDto);
        }
        return transportDto;
    }

    /**
     * Book hotel accommodation.
     */
    @Transactional
    public MarketplaceBookingDto bookHotel(HotelBookingRequest request, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to make a hotel booking");
        }

        HotelEntity hotel = hotelRepo.findById(request.getHotelId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found: " + request.getHotelId()));

        if (hotel.getProviderId() != null) {
            if (Boolean.FALSE.equals(hotel.getActive())) {
                throw new IllegalArgumentException("Hotel is currently inactive");
            }
            com.odyssey.entity.ProviderEntity prov = providerRepo.findById(hotel.getProviderId()).orElse(null);
            if (prov == null || prov.getStatus() == com.odyssey.model.enums.ProviderStatus.SUSPENDED || prov.getStatus() == com.odyssey.model.enums.ProviderStatus.REJECTED) {
                throw new IllegalArgumentException("Hotel provider is currently unavailable");
            }
        }

        long nights = Math.max(1, ChronoUnit.DAYS.between(request.getCheckIn(), request.getCheckOut()));
        int roomsCount = Math.max(1, request.getRooms());

        com.odyssey.entity.HotelRoomTypeEntity roomType = null;
        if (request.getRoomTypeId() != null && !request.getRoomTypeId().isBlank()) {
            roomType = roomTypeRepo.findById(request.getRoomTypeId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Room type not found: " + request.getRoomTypeId()));
            if (!roomType.getHotelId().equals(hotel.getId())) {
                throw new IllegalArgumentException("Room type does not belong to the selected hotel");
            }
            if (Boolean.FALSE.equals(roomType.getActive())) {
                throw new IllegalArgumentException("Room type is inactive");
            }
        } else {
            List<com.odyssey.entity.HotelRoomTypeEntity> roomTypes = roomTypeRepo
                    .findByHotelIdAndActiveTrueOrderByPricePerNightAsc(hotel.getId());
            if (!roomTypes.isEmpty()) {
                roomType = roomTypes.get(0);
            }
        }

        BigDecimal unitPrice = roomType != null ? roomType.getPricePerNight()
                : (hotel.getPricePerNight() != null ? hotel.getPricePerNight() : BigDecimal.valueOf(1500));
        BigDecimal totalCost = unitPrice.multiply(BigDecimal.valueOf(nights * roomsCount));

        if (roomType != null) {
            final com.odyssey.entity.HotelRoomTypeEntity finalRt = roomType;
            for (LocalDate d = request.getCheckIn(); d.isBefore(request.getCheckOut()); d = d.plusDays(1)) {
                final LocalDate stayDate = d;
                HotelRoomAvailabilityEntity avail = availabilityRepo.findForUpdate(finalRt.getId(), stayDate)
                        .orElseGet(() -> {
                            HotelRoomAvailabilityEntity fresh = HotelRoomAvailabilityEntity.builder()
                                    .id(IdGenerator.generate("AVA"))
                                    .roomTypeId(finalRt.getId())
                                    .hotelId(hotel.getId())
                                    .stayDate(stayDate)
                                    .roomsTotal(finalRt.getRoomsTotal())
                                    .roomsBooked(0)
                                    .isClosed(false)
                                    .createdAt(LocalDateTime.now())
                                    .updatedAt(LocalDateTime.now())
                                    .build();
                            return availabilityRepo.saveAndFlush(fresh);
                        });

                if (Boolean.TRUE.equals(avail.getIsClosed())) {
                    throw new ConflictException("Room type is closed on date: " + d);
                }
                int left = avail.getRoomsTotal() - avail.getRoomsBooked();
                if (left < roomsCount) {
                    throw new ConflictException("Not enough rooms available on date " + d + " (sold out)");
                }
                avail.setRoomsBooked(avail.getRoomsBooked() + roomsCount);
                avail.setUpdatedAt(LocalDateTime.now());
                availabilityRepo.save(avail);
            }
        }

        LocalTime checkInTime = LocalTime.of(14, 0);
        LocalTime checkOutTime = LocalTime.of(11, 0);
        if (hotel.getCheckInTime() != null && !hotel.getCheckInTime().isBlank()) {
            try {
                checkInTime = LocalTime.parse(hotel.getCheckInTime().trim());
            } catch (Exception ignored) {
            }
        }
        if (hotel.getCheckOutTime() != null && !hotel.getCheckOutTime().isBlank()) {
            try {
                checkOutTime = LocalTime.parse(hotel.getCheckOutTime().trim());
            } catch (Exception ignored) {
            }
        }

        String bookingId = IdGenerator.generate("BKG");
        String bookingRef = "ODY-" + generateAlphaNumeric(6);

        TripEntity trip = resolveOrCreateTrip(
                request.getTripId(),
                request.getCreateTrip(),
                currentUser,
                "Stay in " + hotel.getCity(),
                totalCost,
                request.getCheckOut().atTime(checkOutTime),
                "HOTEL");

        String providerName = hotel.getName() + (roomType != null ? " - " + roomType.getName() : "");
        boolean isProviderBooking = hotel.getProviderId() != null;
        String initialStatus = isProviderBooking ? "PENDING_PROVIDER_CONFIRMATION" : "CONFIRMED";
        String initialPaymentStatus = isProviderBooking ? "UNPAID" : "DEMO_PAID";
        LocalDateTime expiresAt = isProviderBooking ? LocalDateTime.now().plusSeconds(confirmationTimeoutSeconds) : null;

        BookingEntity booking = BookingEntity.builder()
                .id(bookingId)
                .trip(trip)
                .user(currentUser)
                .type("HOTEL")
                .provider(providerName)
                .providerId(hotel.getProviderId())
                .serviceId(hotel.getId())
                .hotelRoomTypeId(roomType != null ? roomType.getId() : null)
                .fromLocation(hotel.getCity())
                .toLocation(hotel.getAddress())
                .startTime(request.getCheckIn().atTime(checkInTime))
                .endTime(request.getCheckOut().atTime(checkOutTime))
                .cost(totalCost)
                .status(initialStatus)
                .paymentStatus(initialPaymentStatus)
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod().toUpperCase() : "CARD")
                .source(hotel.getProviderId() != null ? "PROVIDER" : "MARKETPLACE")
                .bookingReference(bookingRef)
                .hotelContactEmail(hotel.getContactEmail())
                .passengerNames(request.getGuestName() + " (" + roomsCount + " rooms, " + nights + " nights)")
                .lastStatusState("ON_TIME")
                .lastDelayMinutes(0)
                .lastStatusSource("SIMULATED")
                .lastCheckedAt(LocalDateTime.now())
                .cannotHonour(false)
                .confirmationExpiresAt(expiresAt)
                .providerConfirmationStatus(isProviderBooking ? "PENDING" : null)
                .build();

        calculateDependsOnAndLinkOthers(booking, trip);

        bookingRepo.save(booking);

        AuditEntryEntity audit = AuditEntryEntity.builder()
                .id(IdGenerator.generate("AUD"))
                .trip(trip)
                .userId(currentUser.getId())
                .bookingId(booking.getId())
                .timestamp(LocalDateTime.now())
                .actor("USER")
                .action(isProviderBooking ? "BOOKING_PENDING_PROVIDER_CONFIRMATION" : "BOOKING_ADDED")
                .details(isProviderBooking
                        ? String.format("Hotel booking %s pending provider confirmation (expires in %ds)", bookingRef, confirmationTimeoutSeconds)
                        : String.format("Marketplace booking %s added", bookingRef))
                .build();
        auditRepo.save(audit);

        if (isProviderBooking && trip != null && notificationRepo != null) {
            com.odyssey.entity.ProviderEntity prov = providerRepo.findById(hotel.getProviderId()).orElse(null);
            if (prov != null && prov.getContactEmail() != null) {
                notificationRepo.save(com.odyssey.entity.NotificationEntity.builder()
                        .id(IdGenerator.generate("NTF"))
                        .trip(trip)
                        .eventType("BOOKING_PENDING_CONFIRMATION")
                        .channel("EMAIL")
                        .recipientMasked(prov.getContactEmail())
                        .subject("New Hotel Booking Request - " + bookingRef)
                        .body(String.format("New booking request for %s by %s. Please accept or reject within %d seconds.",
                                hotel.getName(), request.getGuestName(), confirmationTimeoutSeconds))
                        .status("SENT")
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }

        return toDto(booking, null);
    }

    /**
     * Book a cab transfer.
     */
    @Transactional
    public MarketplaceBookingDto bookCab(CabBookingRequest request, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to make a cab booking");
        }

        CabServiceEntity cab = cabRepo.findById(request.getCabServiceId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Cab service not found: " + request.getCabServiceId()));

        if (cab.getProviderId() != null) {
            if (Boolean.FALSE.equals(cab.getActive())) {
                throw new IllegalArgumentException("Cab service is currently inactive");
            }
            com.odyssey.entity.ProviderEntity prov = providerRepo.findById(cab.getProviderId()).orElse(null);
            if (prov == null || prov.getStatus() == com.odyssey.model.enums.ProviderStatus.SUSPENDED || prov.getStatus() == com.odyssey.model.enums.ProviderStatus.REJECTED) {
                throw new IllegalArgumentException("Cab provider is currently unavailable");
            }
        }

        double dist = estimateCabDistance(request.getPickup(), request.getDropoff(), cab.getCity());
        BigDecimal fare = cab.getBaseFare().add(cab.getPerKm().multiply(BigDecimal.valueOf(dist))).setScale(2,
                RoundingMode.HALF_UP);

        String bookingId = IdGenerator.generate("BKG");
        String bookingRef = "ODY-" + generateAlphaNumeric(6);

        TripEntity trip = resolveOrCreateTrip(
                request.getTripId(),
                request.getCreateTrip(),
                currentUser,
                request.getPickup() + " to " + request.getDropoff(),
                fare,
                request.getPickupTime().plusMinutes(45),
                "CAB");

        boolean isProviderBooking = cab.getProviderId() != null;
        String initialStatus = isProviderBooking ? "PENDING_PROVIDER_CONFIRMATION" : "CONFIRMED";
        String initialPaymentStatus = isProviderBooking ? "UNPAID" : "DEMO_PAID";
        LocalDateTime expiresAt = isProviderBooking ? LocalDateTime.now().plusSeconds(confirmationTimeoutSeconds) : null;

        BookingEntity booking = BookingEntity.builder()
                .id(bookingId)
                .trip(trip)
                .user(currentUser)
                .type("CAB")
                .provider(cab.getProvider())
                .providerId(cab.getProviderId())
                .serviceId(cab.getId())
                .vehicleNumber(cab.getVehicleNumber())
                .fromLocation(request.getPickup())
                .toLocation(request.getDropoff())
                .startTime(request.getPickupTime())
                .endTime(request.getPickupTime().plusMinutes(45))
                .cost(fare)
                .status(initialStatus)
                .paymentStatus(initialPaymentStatus)
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod().toUpperCase() : "CARD")
                .source(cab.getProviderId() != null ? "PROVIDER" : "MARKETPLACE")
                .bookingReference(bookingRef)
                .passengerNames("Transfer (" + dist + " km est.)")
                .lastStatusState("ON_TIME")
                .lastDelayMinutes(0)
                .lastStatusSource("SIMULATED")
                .lastCheckedAt(LocalDateTime.now())
                .cannotHonour(false)
                .confirmationExpiresAt(expiresAt)
                .providerConfirmationStatus(isProviderBooking ? "PENDING" : null)
                .build();

        calculateDependsOnAndLinkOthers(booking, trip);

        bookingRepo.save(booking);

        AuditEntryEntity audit = AuditEntryEntity.builder()
                .id(IdGenerator.generate("AUD"))
                .trip(trip)
                .userId(currentUser.getId())
                .bookingId(booking.getId())
                .timestamp(LocalDateTime.now())
                .actor("USER")
                .action(isProviderBooking ? "BOOKING_PENDING_PROVIDER_CONFIRMATION" : "BOOKING_ADDED")
                .details(isProviderBooking
                        ? String.format("Cab booking %s pending provider confirmation (expires in %ds)", bookingRef, confirmationTimeoutSeconds)
                        : String.format("Marketplace booking %s added", bookingRef))
                .build();
        auditRepo.save(audit);

        if (isProviderBooking && trip != null && notificationRepo != null) {
            com.odyssey.entity.ProviderEntity prov = providerRepo.findById(cab.getProviderId()).orElse(null);
            if (prov != null && prov.getContactEmail() != null) {
                notificationRepo.save(com.odyssey.entity.NotificationEntity.builder()
                        .id(IdGenerator.generate("NTF"))
                        .trip(trip)
                        .eventType("BOOKING_PENDING_CONFIRMATION")
                        .channel("EMAIL")
                        .recipientMasked(prov.getContactEmail())
                        .subject("New Cab Booking Request - " + bookingRef)
                        .body(String.format("New cab transfer request from %s to %s. Please accept or reject within %d seconds.",
                                request.getPickup(), request.getDropoff(), confirmationTimeoutSeconds))
                        .status("SENT")
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }

        return toDto(booking, null);
    }

    /**
     * Process mock payment and optionally complete the booking in draft.
     * NEVER store, log or persist card number, expiry or CVV.
     */
    @Transactional
    public MockPaymentResponse processMockPayment(MockPaymentRequest request, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to process payment");
        }

        String mode = request.getPaymentMethod() != null ? request.getPaymentMethod().toUpperCase().trim() : "CARD";
        String last4 = "0000";

        if ("CARD".equals(mode)) {
            String rawCard = request.getCardNumber() != null ? request.getCardNumber().replaceAll("\\s+|-", "") : "";
            if (rawCard.isBlank()) {
                throw new IllegalArgumentException("Card number is required for card payments");
            }
            if (DECLINED_CARD_NUMBER.equals(rawCard) || "4000000000000002".equals(rawCard)) {
                log.info("Demo card declined for test failure card number");
                throw new IllegalArgumentException("Payment failed (demo card declined)");
            }
            last4 = rawCard.length() >= 4 ? rawCard.substring(rawCard.length() - 4) : "0000";
        } else if ("UPI".equals(mode)) {
            String upiId = request.getUpiId() != null ? request.getUpiId().trim() : "";
            if (upiId.isBlank()) {
                throw new IllegalArgumentException("UPI ID is required for UPI payments");
            }
            if (!upiId.matches("^[a-zA-Z0-9.\\-_]{2,49}@[a-zA-Z]{2,49}$")) {
                throw new IllegalArgumentException("Invalid UPI ID format. Expected format: name@bank");
            }
            if ("fail@test".equalsIgnoreCase(upiId)) {
                log.info("Documented UPI failure ID triggered: fail@test");
                throw new IllegalArgumentException("Payment failed (UPI transaction declined)");
            }
            last4 = "UPI";
        } else if ("NETBANKING".equals(mode)) {
            String bank = request.getBankName() != null ? request.getBankName().trim() : "";
            if (bank.isBlank()) {
                throw new IllegalArgumentException("Bank selection is required for Netbanking payments");
            }
            if ("Test Failure Bank".equalsIgnoreCase(bank)) {
                log.info("Documented Netbanking failure bank triggered: Test Failure Bank");
                throw new IllegalArgumentException("Payment failed (Netbanking transaction declined)");
            }
            last4 = "NETB";
        } else {
            throw new IllegalArgumentException("Unsupported payment method: " + mode + ". Must be CARD, UPI, or NETBANKING");
        }

        MarketplaceBookingDto confirmedBooking = null;

        // If bookingDraft is provided, convert and execute the booking
        if (request.getBookingDraft() != null) {
            try {
                com.fasterxml.jackson.databind.JsonNode draftNode = objectMapper.valueToTree(request.getBookingDraft());
                if (draftNode.has("serviceDepartureId")) {
                    MarketplaceBookingRequest transportDraft = objectMapper.treeToValue(
                            draftNode, MarketplaceBookingRequest.class);
                    if (transportDraft.getPaymentMethod() == null) {
                        transportDraft.setPaymentMethod(mode);
                    }
                    confirmedBooking = bookTransport(transportDraft, currentUser);
                    confirmedBooking.setPaymentMethod(mode);
                    confirmedBooking.setLast4Card(last4);
                } else if (draftNode.has("hotelId")) {
                    HotelBookingRequest hotelDraft = objectMapper.treeToValue(
                            draftNode, HotelBookingRequest.class);
                    if (hotelDraft.getPaymentMethod() == null) {
                        hotelDraft.setPaymentMethod(mode);
                    }
                    confirmedBooking = bookHotel(hotelDraft, currentUser);
                    confirmedBooking.setPaymentMethod(mode);
                    confirmedBooking.setLast4Card(last4);
                } else if (draftNode.has("cabServiceId")) {
                    CabBookingRequest cabDraft = objectMapper.treeToValue(
                            draftNode, CabBookingRequest.class);
                    if (cabDraft.getPaymentMethod() == null) {
                        cabDraft.setPaymentMethod(mode);
                    }
                    confirmedBooking = bookCab(cabDraft, currentUser);
                    confirmedBooking.setPaymentMethod(mode);
                    confirmedBooking.setLast4Card(last4);
                }
            } catch (ConflictException | IllegalArgumentException ex) {
                throw ex;
            } catch (Exception e) {
                log.warn("Failed to parse bookingDraft during mock payment: {}", e.getMessage(), e);
            }
        }

        MarketplaceBookingDto pendingAddon = (confirmedBooking != null) ? confirmedBooking.getPendingAddon() : null;

        return MockPaymentResponse.builder()
                .success(true)
                .transactionId("TXN-" + generateAlphaNumeric(10))
                .paymentMethod(mode)
                .cardLast4(last4)
                .message("Demo payment processed successfully")
                .disclaimer(DEMO_DISCLAIMER)
                .confirmedBooking(confirmedBooking)
                .pendingAddonBooking(pendingAddon)
                .build();
    }

    // In-memory draft storage for /checkout/{bookingDraftId}
    private final Map<String, Map<String, Object>> draftStorage = new ConcurrentHashMap<>();

    public Map<String, Object> createBookingDraft(Map<String, Object> draftData) {
        String draftId = IdGenerator.generate("DFT");
        draftData.put("id", draftId);
        draftData.put("createdAt", LocalDateTime.now().toString());
        draftStorage.put(draftId, draftData);
        return draftData;
    }

    public Map<String, Object> getBookingDraft(String draftId) {
        Map<String, Object> draft = draftStorage.get(draftId);
        if (draft == null) {
            throw new ResourceNotFoundException("Booking draft not found: " + draftId);
        }
        return draft;
    }

    @Transactional
    public BookingVerificationResponse requestBookingVerification(BookingVerificationRequest req, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to request booking verification");
        }

        String type = req.getType() != null ? req.getType().toUpperCase().trim() : "HOTEL";
        String bookingRef = "ODY-" + generateAlphaNumeric(6);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(confirmationTimeoutSeconds);

        String providerEmail = "provider@odyssey.com";
        String targetName = req.getTargetName();
        String bookingId = IdGenerator.generate("BKG");
        BookingEntity booking = null;

        TripEntity trip = null;
        if (req.getTripId() != null && !req.getTripId().isBlank()) {
            trip = tripRepo.findById(req.getTripId()).orElse(null);
        }
        if (trip == null) {
            trip = resolveOrCreateTrip(
                    req.getTripId(),
                    true,
                    currentUser,
                    "HOTEL".equals(type) ? "Stay in " + (req.getCity() != null ? req.getCity() : "Destination") : "Cab transfer in " + (req.getCity() != null ? req.getCity() : "Destination"),
                    BigDecimal.valueOf(1000),
                    LocalDateTime.now().plusDays(1),
                    type);
        }

        if ("HOTEL".equals(type)) {
            HotelEntity hotel = hotelRepo.findById(req.getTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Hotel not found: " + req.getTargetId()));
            if (targetName == null || targetName.isBlank()) {
                targetName = hotel.getName();
            }

            String providerId = hotel.getProviderId();
            if (providerId != null) {
                ProviderEntity prov = providerRepo.findById(providerId).orElse(null);
                if (prov != null && prov.getContactEmail() != null) {
                    providerEmail = prov.getContactEmail();
                }
            } else if (hotel.getContactEmail() != null && !hotel.getContactEmail().isBlank()) {
                providerEmail = hotel.getContactEmail();
            } else {
                providerEmail = "reservations@" + hotel.getCity().toLowerCase().replaceAll("\\s+", "") + "hotels.com";
            }

            LocalDate checkIn = req.getCheckIn() != null ? req.getCheckIn() : LocalDate.now();
            LocalDate checkOut = req.getCheckOut() != null ? req.getCheckOut() : checkIn.plusDays(req.getNights() != null ? req.getNights() : 1);
            int rooms = req.getRooms() != null && req.getRooms() > 0 ? req.getRooms() : 1;
            long nights = Math.max(1, ChronoUnit.DAYS.between(checkIn, checkOut));
            BigDecimal price = hotel.getPricePerNight() != null ? hotel.getPricePerNight() : BigDecimal.valueOf(2000);
            BigDecimal totalCost = price.multiply(BigDecimal.valueOf(nights * rooms));

            booking = BookingEntity.builder()
                    .id(bookingId)
                    .trip(trip)
                    .user(currentUser)
                    .type("HOTEL")
                    .provider(targetName)
                    .providerId(providerId)
                    .serviceId(hotel.getId())
                    .hotelRoomTypeId(req.getRoomTypeId())
                    .fromLocation(hotel.getCity())
                    .toLocation(hotel.getAddress() != null ? hotel.getAddress() : hotel.getName())
                    .startTime(checkIn.atTime(14, 0))
                    .endTime(checkOut.atTime(11, 0))
                    .cost(totalCost)
                    .status("PENDING_PROVIDER_CONFIRMATION")
                    .paymentStatus("UNPAID")
                    .paymentMethod("CARD")
                    .source(providerId != null ? "PROVIDER" : "MARKETPLACE")
                    .bookingReference(bookingRef)
                    .hotelContactEmail(providerEmail)
                    .passengerNames((req.getGuestName() != null ? req.getGuestName() : currentUser.getName()) + " (" + rooms + " rooms)")
                    .lastStatusState("ON_TIME")
                    .lastDelayMinutes(0)
                    .lastStatusSource("SIMULATED")
                    .lastCheckedAt(LocalDateTime.now())
                    .confirmationExpiresAt(expiresAt)
                    .providerConfirmationStatus("PENDING")
                    .build();

            bookingRepo.save(booking);

        } else if ("CAB".equals(type)) {
            CabServiceEntity cab = cabRepo.findById(req.getTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cab service not found: " + req.getTargetId()));
            if (targetName == null || targetName.isBlank()) {
                targetName = cab.getProvider();
            }

            String providerId = cab.getProviderId();
            if (providerId != null) {
                ProviderEntity prov = providerRepo.findById(providerId).orElse(null);
                if (prov != null && prov.getContactEmail() != null) {
                    providerEmail = prov.getContactEmail();
                }
            } else {
                providerEmail = "dispatch@" + (cab.getProvider() != null ? cab.getProvider().toLowerCase().replaceAll("\\s+", "") : "cab") + ".com";
            }

            String pickup = req.getPickup() != null ? req.getPickup() : (cab.getCity() + " Bus Stand");
            String dropoff = req.getDropoff() != null ? req.getDropoff() : (cab.getCity() + " Center");
            double dist = estimateCabDistance(pickup, dropoff, cab.getCity());
            BigDecimal fare = cab.getBaseFare().add(cab.getPerKm().multiply(BigDecimal.valueOf(dist))).setScale(2, RoundingMode.HALF_UP);

            booking = BookingEntity.builder()
                    .id(bookingId)
                    .trip(trip)
                    .user(currentUser)
                    .type("CAB")
                    .provider(targetName)
                    .providerId(providerId)
                    .serviceId(cab.getId())
                    .fromLocation(pickup)
                    .toLocation(dropoff)
                    .startTime(LocalDateTime.now().plusHours(1))
                    .endTime(LocalDateTime.now().plusHours(1).plusMinutes((long) (dist * 3)))
                    .cost(fare)
                    .status("PENDING_PROVIDER_CONFIRMATION")
                    .paymentStatus("UNPAID")
                    .paymentMethod("CARD")
                    .source(providerId != null ? "PROVIDER" : "MARKETPLACE")
                    .bookingReference(bookingRef)
                    .passengerNames(currentUser.getName() != null ? currentUser.getName() : "Traveller")
                    .lastStatusState("ON_TIME")
                    .lastDelayMinutes(0)
                    .lastStatusSource("SIMULATED")
                    .lastCheckedAt(LocalDateTime.now())
                    .confirmationExpiresAt(expiresAt)
                    .providerConfirmationStatus("PENDING")
                    .build();

            bookingRepo.save(booking);
        } else {
            throw new IllegalArgumentException("Unsupported verification type: " + type);
        }

        // Record audit entry
        AuditEntryEntity audit = AuditEntryEntity.builder()
                .id(IdGenerator.generate("AUD"))
                .trip(trip)
                .userId(currentUser.getId())
                .bookingId(booking.getId())
                .timestamp(LocalDateTime.now())
                .actor("USER")
                .action("BOOKING_PENDING_PROVIDER_CONFIRMATION")
                .details(String.format("Verification request sent to provider %s (%s). Expires in %ds.",
                        providerEmail, type, confirmationTimeoutSeconds))
                .build();
        auditRepo.save(audit);

        // Send Email notification to provider
        if (notificationRepo != null) {
            notificationRepo.save(NotificationEntity.builder()
                    .id(IdGenerator.generate("NTF"))
                    .trip(trip)
                    .eventType("BOOKING_PENDING_CONFIRMATION")
                    .channel("EMAIL")
                    .recipientMasked(providerEmail)
                    .subject("Urgent: " + type + " Booking Verification Request - " + bookingRef)
                    .body(String.format("Traveller %s has selected your %s (%s). Please verify and accept this booking within %d seconds.",
                            currentUser.getName() != null ? currentUser.getName() : "Traveller",
                            targetName, type, confirmationTimeoutSeconds))
                    .status("SENT")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        return BookingVerificationResponse.builder()
                .success(true)
                .bookingId(booking.getId())
                .bookingReference(bookingRef)
                .type(type)
                .targetName(targetName)
                .providerEmail(providerEmail)
                .timeoutSeconds(confirmationTimeoutSeconds)
                .expiresAt(expiresAt)
                .status("PENDING_PROVIDER_CONFIRMATION")
                .message("Verification email sent to provider. Please wait for acceptance within " + confirmationTimeoutSeconds + " seconds.")
                .build();
    }

    @Transactional
    public MarketplaceBookingDto getBookingStatus(String bookingId, UserEntity currentUser) {
        BookingEntity b = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (b.getUser() != null && currentUser != null && !b.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Cannot view another user's booking status");
        }

        // Check if pending confirmation has timed out
        if ("PENDING_PROVIDER_CONFIRMATION".equalsIgnoreCase(b.getStatus())
                && b.getConfirmationExpiresAt() != null
                && LocalDateTime.now().isAfter(b.getConfirmationExpiresAt())) {
            b.setStatus("CANCELLED");
            b.setProviderConfirmationStatus("EXPIRED");
            b.setCannotHonour(true);
            b.setCannotHonourReason("Provider confirmation window expired");
            b.setCannotHonourAt(LocalDateTime.now());
            bookingRepo.save(b);

            AuditEntryEntity audit = AuditEntryEntity.builder()
                    .id(IdGenerator.generate("AUD"))
                    .trip(b.getTrip())
                    .userId(currentUser != null ? currentUser.getId() : null)
                    .bookingId(b.getId())
                    .timestamp(LocalDateTime.now())
                    .actor("SYSTEM")
                    .action("PROVIDER_CONFIRMATION_TIMEOUT")
                    .details(String.format("Provider confirmation window expired for booking %s (%s). Auto-cancelled.",
                            b.getBookingReference(), b.getType()))
                    .build();
            auditRepo.save(audit);
        }

        return toDto(b, null);
    }

    /**
     * Retrieve current user's marketplace bookings.
     */
    @Transactional(readOnly = true)
    public List<MarketplaceBookingDto> getMyBookings(UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return bookingRepo.findByUserIdOrderByStartTimeDesc(currentUser.getId()).stream()
                .map(b -> toDto(b, null))
                .collect(Collectors.toList());
    }

    /**
     * Retrieve a specific booking with user ownership check.
     */
    @Transactional(readOnly = true)
    public MarketplaceBookingDto getBookingById(String id, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }
        BookingEntity booking = bookingRepo.findById(id)
                .or(() -> bookingRepo.findByBookingReference(id))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));

        if (booking.getUser() == null || !booking.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Cannot view another user's booking");
        }

        return toDto(booking, null);
    }

    /**
     * Cancel booking: releases seat holds, increases seats_available, refunds
     * payment.
     */
    @Transactional
    public MarketplaceBookingDto cancelBooking(String id, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }
        BookingEntity booking = bookingRepo.findById(id)
                .or(() -> bookingRepo.findByBookingReference(id))
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));

        if (booking.getUser() == null || !booking.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Cannot cancel another user's booking");
        }

        if ("CANCELLED".equalsIgnoreCase(booking.getStatus())) {
            return toDto(booking, null);
        }

        // Release seats if this booking holds departure seats
        if (booking.getDepartureId() != null) {
            List<SeatHoldEntity> holds = seatHoldRepo.findByBookingId(booking.getId());
            if (!holds.isEmpty()) {
                int seatsToRelease = holds.size();
                departureRepo.findById(booking.getDepartureId()).ifPresent(dep -> {
                    dep.setSeatsAvailable(dep.getSeatsAvailable() + seatsToRelease);
                    departureRepo.save(dep);
                });
                seatHoldRepo.deleteByBookingId(booking.getId());
            }
        }

        // Release hotel rooms if this is a hotel booking
        if ("HOTEL".equalsIgnoreCase(booking.getType()) && booking.getHotelRoomTypeId() != null
                && booking.getStartTime() != null && booking.getEndTime() != null && availabilityRepo != null) {
            try {
                LocalDate start = booking.getStartTime().toLocalDate();
                LocalDate end = booking.getEndTime().toLocalDate();
                for (LocalDate d = start; d.isBefore(end); d = d.plusDays(1)) {
                    availabilityRepo.findByRoomTypeIdAndStayDate(booking.getHotelRoomTypeId(), d).ifPresent(avail -> {
                        avail.setRoomsBooked(Math.max(0, avail.getRoomsBooked() - 1));
                        availabilityRepo.save(avail);
                    });
                }
            } catch (Exception ex) {
                log.warn("Failed to release hotel room availability for booking {}: {}", booking.getId(), ex.getMessage());
            }
        }

        String bookingType = booking.getType() != null ? booking.getType().toUpperCase() : "BUS";
        LocalDateTime now = LocalDateTime.now();
        booking.setCancelledAt(now);
        booking.setCancelledBy(currentUser.getName() != null && !currentUser.getName().isBlank()
                ? currentUser.getName() : currentUser.getEmail());

        BigDecimal grossFare = booking.getCost() != null ? booking.getCost() : BigDecimal.ZERO;
        BigDecimal refundAmount = BigDecimal.ZERO;
        BigDecimal deductionAmount = BigDecimal.ZERO;
        String refundTierApplied = null;

        if ("CAB".equalsIgnoreCase(bookingType) || "CAB_OFFERING".equalsIgnoreCase(bookingType)) {
            // Requirement 6: CAB CANCELLATION — TRAVELLER MUST REQUEST REFUND
            booking.setStatus("CANCELLED");
            booking.setPaymentStatus("PAID");
            refundTierApplied = "Cabs do not auto-refund upon cancellation. Submit a refund request for provider review.";
        } else if ("HOTEL".equalsIgnoreCase(bookingType)) {
            // Requirement 5: HOTEL CANCELLATION — 0% REFUND ALWAYS
            booking.setStatus("CANCELLED");
            booking.setPaymentStatus("NON_REFUNDABLE");
            refundAmount = BigDecimal.ZERO;
            deductionAmount = grossFare;
            refundTierApplied = "Hotel booking is strictly non-refundable once confirmed per hotel partner policy";

            if (booking.getTrip() != null && tripLedgerEntryJpaRepository != null && deductionAmount.compareTo(BigDecimal.ZERO) > 0) {
                TripLedgerEntryEntity deductionEntry = TripLedgerEntryEntity.builder()
                        .id(IdGenerator.generate("LED"))
                        .trip(booking.getTrip())
                        .bookingId(booking.getId())
                        .type(com.odyssey.model.enums.LedgerEntryType.REFUND_DEDUCTION)
                        .amount(deductionAmount)
                        .basis(com.odyssey.model.enums.LedgerBasis.RECORDED)
                        .category("HOTEL_NON_REFUNDABLE")
                        .description("Hotel booking is strictly non-refundable once confirmed per hotel partner policy")
                        .refundStatus("POLICY_APPLIED")
                        .receivedOn(LocalDate.now())
                        .createdAt(now)
                        .build();
                tripLedgerEntryJpaRepository.save(deductionEntry);
            }
        } else {
            // Requirement 4: BUS, TRAIN, FLIGHT — TIERED REFUND POLICY
            booking.setStatus("CANCELLED");
            LocalDateTime departureTime = booking.getStartTime();
            com.odyssey.config.CancellationPolicyProperties policy = cancellationPolicyProperties != null
                    ? cancellationPolicyProperties
                    : new com.odyssey.config.CancellationPolicyProperties();

            com.odyssey.config.CancellationPolicyProperties.RefundTierResult tierResult = policy.calculateRefundTier(now, departureTime);
            int refundPercent = tierResult.refundPercent();
            int deductionPercent = tierResult.deductionPercent();
            refundTierApplied = tierResult.description();

            refundAmount = grossFare.multiply(BigDecimal.valueOf(refundPercent))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            deductionAmount = grossFare.subtract(refundAmount);

            if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                booking.setPaymentStatus(deductionAmount.compareTo(BigDecimal.ZERO) > 0 ? "PARTIALLY_REFUNDED" : "DEMO_REFUNDED");
            } else {
                booking.setPaymentStatus("NON_REFUNDABLE");
            }

            if (booking.getTrip() != null && tripLedgerEntryJpaRepository != null) {
                String mainRefundEntryId = null;
                if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                    mainRefundEntryId = IdGenerator.generate("LED");
                    TripLedgerEntryEntity refundEntry = TripLedgerEntryEntity.builder()
                            .id(mainRefundEntryId)
                            .trip(booking.getTrip())
                            .bookingId(booking.getId())
                            .type(com.odyssey.model.enums.LedgerEntryType.REFUND_RECORDED)
                            .amount(refundAmount)
                            .basis(com.odyssey.model.enums.LedgerBasis.RECORDED)
                            .category("CANCELLATION_REFUND")
                            .description(String.format("Refund for cancelled %s booking (%d%% tier)", bookingType, refundPercent))
                            .refundStatus("RECEIVED")
                            .receivedOn(LocalDate.now())
                            .createdAt(now)
                            .build();
                    tripLedgerEntryJpaRepository.save(refundEntry);
                }

                if (deductionAmount.compareTo(BigDecimal.ZERO) > 0) {
                    TripLedgerEntryEntity deductionEntry = TripLedgerEntryEntity.builder()
                            .id(IdGenerator.generate("LED"))
                            .trip(booking.getTrip())
                            .bookingId(booking.getId())
                            .type(com.odyssey.model.enums.LedgerEntryType.REFUND_DEDUCTION)
                            .amount(deductionAmount)
                            .basis(com.odyssey.model.enums.LedgerBasis.RECORDED)
                            .category("CANCELLATION_FEE")
                            .description(tierResult.description())
                            .parentEntryId(mainRefundEntryId)
                            .refundStatus("POLICY_APPLIED")
                            .receivedOn(LocalDate.now())
                            .createdAt(now)
                            .build();
                    tripLedgerEntryJpaRepository.save(deductionEntry);
                }
            }
        }

        bookingRepo.save(booking);

        AuditEntryEntity audit = AuditEntryEntity.builder()
                .id(IdGenerator.generate("AUD"))
                .trip(booking.getTrip())
                .userId(currentUser.getId())
                .bookingId(booking.getId())
                .timestamp(LocalDateTime.now())
                .actor("USER")
                .action("BOOKING_CANCELLED")
                .details(String.format("Marketplace booking %s cancelled. %s", booking.getBookingReference(),
                        refundTierApplied != null ? refundTierApplied : ""))
                .build();
        auditRepo.save(audit);

        log.info("Marketplace booking cancelled: ref={}, id={}, refund={}, deduction={}",
                booking.getBookingReference(), booking.getId(), refundAmount, deductionAmount);
        MarketplaceBookingDto dto = toDto(booking, null);
        dto.setRefundTierApplied(refundTierApplied);
        dto.setRefundAmount(refundAmount);
        dto.setDeductionAmount(deductionAmount);
        return dto;
    }

    /**
     * Scheduled cleanup of draft seat holds older than holdMinutes.
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void cleanupExpiredDraftHolds() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(holdMinutes);
        List<SeatHoldEntity> expiredHolds = seatHoldRepo.findByStatusAndCreatedAtBefore("HELD", cutoff);
        if (!expiredHolds.isEmpty()) {
            Map<String, Long> countPerDeparture = expiredHolds.stream()
                    .collect(Collectors.groupingBy(h -> h.getDeparture().getId(), Collectors.counting()));

            for (Map.Entry<String, Long> entry : countPerDeparture.entrySet()) {
                departureRepo.findById(entry.getKey()).ifPresent(dep -> {
                    dep.setSeatsAvailable(dep.getSeatsAvailable() + entry.getValue().intValue());
                    departureRepo.save(dep);
                });
            }
            seatHoldRepo.deleteAll(expiredHolds);
            log.info("Released {} expired draft seat holds older than {} minutes", expiredHolds.size(), holdMinutes);
        }
    }

    /**
     * Generate printable HTML booking summary with required demo disclaimer.
     */
    @Transactional(readOnly = true)
    public String generatePrintableSummary(String id, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }
        BookingEntity b = bookingRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));

        if (b.getUser() == null || !b.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Cannot access another user's booking summary");
        }

        String ref = escapeHtml(b.getBookingReference() != null ? b.getBookingReference() : b.getId());
        String provider = escapeHtml(b.getProvider());
        String vehicle = escapeHtml(b.getVehicleNumber() != null ? b.getVehicleNumber() : "N/A");
        String origin = escapeHtml(b.getFromLocation());
        String destination = escapeHtml(b.getToLocation());
        String depTime = b.getStartTime() != null ? b.getStartTime().toString().replace("T", " ") : "N/A";
        String arrTime = b.getEndTime() != null ? b.getEndTime().toString().replace("T", " ") : "N/A";
        String seats = escapeHtml(b.getSeatNumbers() != null ? b.getSeatNumbers() : "Assigned at boarding");
        String passengers = escapeHtml(b.getPassengerNames() != null ? b.getPassengerNames() : "Traveller");
        String cost = b.getCost() != null ? "\u20B9" + b.getCost().toPlainString() : "\u20B90.00";
        String status = escapeHtml(b.getStatus());
        String payment = escapeHtml(b.getPaymentStatus() != null ? b.getPaymentStatus() : "DEMO_PAID");

        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <title>Odyssey Booking Summary - " + ref + "</title>\n" +
                "  <style>\n" +
                "    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: #0c1017; color: #e6edf3; padding: 40px; margin: 0; line-height: 1.6; }\n"
                +
                "    .container { max-width: 720px; margin: 0 auto; background: #161b22; border: 1px solid #30363d; border-radius: 12px; padding: 32px; box-shadow: 0 8px 24px rgba(0,0,0,0.5); }\n"
                +
                "    .demo-banner { background: #382405; border: 1px solid #d29922; color: #f0883e; padding: 12px 16px; border-radius: 8px; font-weight: 600; text-align: center; margin-bottom: 24px; font-size: 14px; letter-spacing: 0.3px; }\n"
                +
                "    .header { display: flex; justify-content: space-between; align-items: baseline; border-bottom: 1px solid #21262d; padding-bottom: 16px; margin-bottom: 24px; }\n"
                +
                "    .brand { font-size: 24px; font-weight: 700; color: #58a6ff; letter-spacing: 1px; text-transform: uppercase; }\n"
                +
                "    .ref { font-family: monospace; font-size: 18px; color: #7ee787; background: #0d1117; padding: 4px 10px; border-radius: 6px; border: 1px solid #30363d; }\n"
                +
                "    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 24px; }\n" +
                "    .field { background: #0d1117; padding: 12px 16px; border-radius: 8px; border: 1px solid #21262d; }\n"
                +
                "    .label { font-size: 11px; text-transform: uppercase; color: #8b949e; letter-spacing: 0.5px; margin-bottom: 4px; }\n"
                +
                "    .val { font-size: 15px; font-weight: 500; color: #f0f6fc; }\n" +
                "    .price-box { background: #1f2937; border: 1px solid #388bfd; border-radius: 8px; padding: 16px; text-align: right; margin-top: 16px; }\n"
                +
                "    .price-label { font-size: 13px; color: #8b949e; }\n" +
                "    .price-val { font-size: 24px; font-weight: 700; color: #58a6ff; }\n" +
                "    .actions { margin-top: 32px; text-align: center; }\n" +
                "    .btn-print { background: #238636; color: #ffffff; border: none; padding: 10px 24px; border-radius: 6px; font-size: 14px; font-weight: 600; cursor: pointer; }\n"
                +
                "    @media print {\n" +
                "      body { background: #fff !important; color: #000 !important; padding: 0; }\n" +
                "      .container { border: none; box-shadow: none; background: #fff !important; color: #000 !important; }\n"
                +
                "      .demo-banner { background: #fff3cd !important; color: #856404 !important; border: 1px solid #ffeeba !important; }\n"
                +
                "      .field, .price-box { background: #f8f9fa !important; border-color: #dee2e6 !important; color: #000 !important; }\n"
                +
                "      .brand, .price-val { color: #0056b3 !important; }\n" +
                "      .ref { color: #155724 !important; background: #d4edda !important; }\n" +
                "      .label { color: #6c757d !important; }\n" +
                "      .val { color: #212529 !important; }\n" +
                "      .actions { display: none !important; }\n" +
                "    }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"container\">\n" +
                "    <div class=\"demo-banner\">" + DEMO_DISCLAIMER + "</div>\n" +
                "    <!-- Demo marketplace. No real ticket is issued and no money was charged. -->\n" +
                "    <div class=\"header\">\n" +
                "      <div class=\"brand\">Odyssey Marketplace</div>\n" +
                "      <div class=\"ref\">" + ref + "</div>\n" +
                "    </div>\n" +
                "    <div class=\"grid\">\n" +
                "      <div class=\"field\"><div class=\"label\">Route</div><div class=\"val\">" + origin + " &rarr; "
                + destination + "</div></div>\n" +
                "      <div class=\"field\"><div class=\"label\">Vehicle / Service</div><div class=\"val\">" + provider
                + " (" + vehicle + ")</div></div>\n" +
                "      <div class=\"field\"><div class=\"label\">Departure</div><div class=\"val\">" + depTime
                + "</div></div>\n" +
                "      <div class=\"field\"><div class=\"label\">Arrival</div><div class=\"val\">" + arrTime
                + "</div></div>\n" +
                "      <div class=\"field\"><div class=\"label\">Seat Number(s)</div><div class=\"val\">" + seats
                + "</div></div>\n" +
                "      <div class=\"field\"><div class=\"label\">Passenger(s)</div><div class=\"val\">" + passengers
                + "</div></div>\n" +
                "      <div class=\"field\"><div class=\"label\">Booking Status</div><div class=\"val\">" + status
                + "</div></div>\n" +
                "      <div class=\"field\"><div class=\"label\">Payment Status</div><div class=\"val\">" + payment
                + "</div></div>\n" +
                "    </div>\n" +
                "    <div class=\"price-box\">\n" +
                "      <span class=\"price-label\">Total Demo Fare: </span>\n" +
                "      <span class=\"price-val\">" + cost + "</span>\n" +
                "    </div>\n" +
                "    <div class=\"actions\">\n" +
                "      <button class=\"btn-print\" onclick=\"window.print()\">Print / Save Summary</button>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "</body>\n" +
                "</html>";
    }

    private MarketplaceBookingDto toDto(BookingEntity b, String last4) {
        String scheduleLabel = null;
        if (b.getServiceId() != null) {
            ServiceEntity s = serviceRepo.findById(b.getServiceId()).orElse(null);
            if (s != null && "GENERATED".equalsIgnoreCase(s.getSource())) {
                scheduleLabel = "Generated schedule, not an official timetable";
            }
        }

        String provStatus = null;
        String provBiz = null;
        String provPhone = null;
        String cabContact = null;
        if (b.getProviderId() != null && providerRepo != null) {
            try {
                com.odyssey.entity.ProviderEntity prov = providerRepo.findById(b.getProviderId()).orElse(null);
                if (prov != null) {
                    provStatus = prov.getStatus() != null ? prov.getStatus().name() : null;
                    provBiz = prov.getBusinessName();
                    provPhone = prov.getContactPhone();
                    cabContact = "Cab service contact: " + provBiz + " — " + provPhone;
                }
            } catch (Exception ignored) {
            }
        }

        return MarketplaceBookingDto.builder()
                .id(b.getId())
                .bookingReference(b.getBookingReference())
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
                .seatNumbers(b.getSeatNumbers())
                .passengerNames(b.getPassengerNames())
                .tripId(b.getTrip() != null ? b.getTrip().getId() : null)
                .tripTitle(b.getTrip() != null ? b.getTrip().getTitle() : null)
                .last4Card(last4)
                .scheduleLabel(scheduleLabel)
                .disclaimer(DEMO_DISCLAIMER)
                .hotelContactEmail(b.getHotelContactEmail())
                .providerStatus(provStatus)
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

    private TripEntity resolveOrCreateTrip(String tripId, Boolean createTrip, UserEntity currentUser,
            String title, BigDecimal totalCost, LocalDateTime arrivalTime, String mode) {
        if (Boolean.TRUE.equals(createTrip)) {
            String genTripId = IdGenerator.generate("TRIP");
            String travellerName = (currentUser.getName() != null && !currentUser.getName().isBlank())
                    ? currentUser.getName()
                    : "Traveller";
            String phone = normalizePhoneOrNull(currentUser.getPhone());

            TripEntity trip = TripEntity.builder()
                    .id(genTripId)
                    .title(toTitleCase(title))
                    .travellerName(travellerName)
                    .travellerEmail(currentUser.getEmail())
                    .travellerPhone(phone)
                    .status("CONFIRMED")
                    .userId(currentUser.getId())
                    .user(currentUser)
                    .needsDestinationCab(false)
                    .needsHotel(false)
                    .notifyEmail(true)
                    .notifySms(true)
                    .notifyWhatsapp(false)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            BigDecimal budget = totalCost != null
                    ? totalCost.multiply(BigDecimal.valueOf(3)).setScale(2, RoundingMode.HALF_UP)
                    : new BigDecimal("15000.00");

            TripConstraintsEntity constraints = TripConstraintsEntity.builder()
                    .id(IdGenerator.generate("CON"))
                    .trip(trip)
                    .maxBudget(budget)
                    .mustArriveBy(arrivalTime != null ? arrivalTime : LocalDateTime.now().plusDays(1))
                    .avoidOvernightTravel(false)
                    .preferredModes(mode)
                    .build();

            trip.setConstraints(constraints);
            return tripRepo.save(trip);
        } else if (tripId != null && !tripId.isBlank()) {
            TripEntity existing = tripRepo.findById(tripId)
                    .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));
            String ownerId = existing.getUser() != null ? existing.getUser().getId() : existing.getUserId();
            if (ownerId == null || !ownerId.equals(currentUser.getId())) {
                throw new AccessDeniedException("Access denied: you do not have permission to modify this trip");
            }
            return existing;
        }
        return null;
    }

    private List<String> calculateDependsOnAndLinkOthers(BookingEntity newBooking, TripEntity trip) {
        if (trip == null) {
            return new ArrayList<>();
        }

        List<BookingEntity> tripBookings = bookingRepo.findByTripIdOrderByStartTimeAsc(trip.getId());
        List<String> dependsOn = new ArrayList<>();
        String type = newBooking.getType() != null ? newBooking.getType().toUpperCase() : "";

        if ("CAB".equals(type)) {
            // Cab depends on the arriving bus, train or flight
            Optional<BookingEntity> arrivingTransit = tripBookings.stream()
                    .filter(b -> List.of("BUS", "TRAIN", "FLIGHT").contains(b.getType().toUpperCase()))
                    .filter(b -> !"CANCELLED".equalsIgnoreCase(b.getStatus()))
                    .filter(b -> !b.getEndTime().isAfter(newBooking.getStartTime()))
                    .max(Comparator.comparing(BookingEntity::getEndTime));

            if (arrivingTransit.isPresent()) {
                dependsOn.add(arrivingTransit.get().getId());
            } else {
                // If overlap exists where transit ends after cab pickup, warn only
                Optional<BookingEntity> overlappingTransit = tripBookings.stream()
                        .filter(b -> List.of("BUS", "TRAIN", "FLIGHT").contains(b.getType().toUpperCase()))
                        .filter(b -> !"CANCELLED".equalsIgnoreCase(b.getStatus()))
                        .filter(b -> b.getEndTime().isAfter(newBooking.getStartTime()))
                        .findFirst();
                if (overlappingTransit.isPresent()) {
                    log.warn("Cab pickup time {} is before arriving transit end time {}",
                            newBooking.getStartTime(), overlappingTransit.get().getEndTime());
                    dependsOn.add(overlappingTransit.get().getId());
                }
            }

            // Link any unlinked hotel to this cab if times make sense
            for (BookingEntity existing : tripBookings) {
                if ("HOTEL".equalsIgnoreCase(existing.getType()) &&
                        (existing.getDependsOn() == null || existing.getDependsOn().isEmpty())) {
                    if (!newBooking.getEndTime().isAfter(existing.getStartTime())) {
                        existing.getDependsOn().add(newBooking.getId());
                        bookingRepo.save(existing);
                        log.info("Linked unlinked hotel {} to new cab {}", existing.getId(), newBooking.getId());
                    }
                }
            }
        } else if ("HOTEL".equals(type)) {
            // Hotel depends on the cab, or on the arriving leg when no cab exists
            Optional<BookingEntity> priorCab = tripBookings.stream()
                    .filter(b -> "CAB".equalsIgnoreCase(b.getType()) && !"CANCELLED".equalsIgnoreCase(b.getStatus()))
                    .filter(b -> !b.getEndTime().isAfter(newBooking.getStartTime()))
                    .max(Comparator.comparing(BookingEntity::getEndTime));

            if (priorCab.isPresent()) {
                dependsOn.add(priorCab.get().getId());
            } else {
                Optional<BookingEntity> anyCab = tripBookings.stream()
                        .filter(b -> "CAB".equalsIgnoreCase(b.getType()) && !"CANCELLED".equalsIgnoreCase(b.getStatus()))
                        .max(Comparator.comparing(BookingEntity::getEndTime));

                if (anyCab.isPresent()) {
                    dependsOn.add(anyCab.get().getId());
                } else {
                    Optional<BookingEntity> arrivingTransit = tripBookings.stream()
                            .filter(b -> List.of("BUS", "TRAIN", "FLIGHT").contains(b.getType().toUpperCase()))
                            .filter(b -> !"CANCELLED".equalsIgnoreCase(b.getStatus()))
                            .filter(b -> !b.getEndTime().isAfter(newBooking.getStartTime()))
                            .max(Comparator.comparing(BookingEntity::getEndTime));

                    if (arrivingTransit.isPresent()) {
                        dependsOn.add(arrivingTransit.get().getId());
                    } else {
                        // Overlap check
                        Optional<BookingEntity> overlappingTransit = tripBookings.stream()
                                .filter(b -> List.of("BUS", "TRAIN", "FLIGHT").contains(b.getType().toUpperCase()))
                                .filter(b -> !"CANCELLED".equalsIgnoreCase(b.getStatus()))
                                .filter(b -> b.getEndTime().isAfter(newBooking.getStartTime()))
                                .findFirst();
                        if (overlappingTransit.isPresent()) {
                            log.warn("Hotel check-in {} is before preceding leg end time {}",
                                    newBooking.getStartTime(), overlappingTransit.get().getEndTime());
                            dependsOn.add(overlappingTransit.get().getId());
                        }
                    }
                }
            }
        } else if (List.of("BUS", "TRAIN", "FLIGHT").contains(type)) {
            // When a transport leg is added later, link an unlinked cab or hotel to it if
            // times make sense
            for (BookingEntity existing : tripBookings) {
                if ("CAB".equalsIgnoreCase(existing.getType()) &&
                        (existing.getDependsOn() == null || existing.getDependsOn().isEmpty())) {
                    if (!newBooking.getEndTime().isAfter(existing.getStartTime())) {
                        existing.getDependsOn().add(newBooking.getId());
                        bookingRepo.save(existing);
                        log.info("Linked unlinked cab {} to new transit {}", existing.getId(), newBooking.getId());
                    }
                } else if ("HOTEL".equalsIgnoreCase(existing.getType()) &&
                        (existing.getDependsOn() == null || existing.getDependsOn().isEmpty())) {
                    boolean hasCab = tripBookings.stream().anyMatch(
                            b -> "CAB".equalsIgnoreCase(b.getType()) && !"CANCELLED".equalsIgnoreCase(b.getStatus()));
                    if (!hasCab && !newBooking.getEndTime().isAfter(existing.getStartTime())) {
                        existing.getDependsOn().add(newBooking.getId());
                        bookingRepo.save(existing);
                        log.info("Linked unlinked hotel {} to new transit {}", existing.getId(), newBooking.getId());
                    }
                }
            }
        }

        // Verify cycle
        newBooking.setDependsOn(dependsOn);
        List<BookingEntity> all = new ArrayList<>(tripBookings);
        all.add(newBooking);
        if (hasCycle(all)) {
            throw new IllegalArgumentException("Dependency cycle detected in bookings");
        }

        return dependsOn;
    }

    private boolean hasCycle(List<BookingEntity> bookings) {
        if (bookings == null || bookings.isEmpty()) {
            return false;
        }
        Map<String, List<String>> adj = new HashMap<>();
        for (BookingEntity b : bookings) {
            adj.putIfAbsent(b.getId(), new ArrayList<>());
            if (b.getDependsOn() != null) {
                for (String dep : b.getDependsOn()) {
                    if (dep != null && !dep.isBlank()) {
                        adj.get(b.getId()).add(dep);
                    }
                }
            }
        }

        Map<String, Integer> state = new HashMap<>();
        for (String id : adj.keySet()) {
            if (state.getOrDefault(id, 0) == 0) {
                if (dfsDetectCycle(id, adj, state)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean dfsDetectCycle(String node, Map<String, List<String>> adj, Map<String, Integer> state) {
        state.put(node, 1);
        List<String> neighbors = adj.getOrDefault(node, Collections.emptyList());
        for (String neighbor : neighbors) {
            int neighborState = state.getOrDefault(neighbor, 0);
            if (neighborState == 1) {
                return true;
            }
            if (neighborState == 0) {
                if (dfsDetectCycle(neighbor, adj, state)) {
                    return true;
                }
            }
        }
        state.put(node, 2);
        return false;
    }

    private String toTitleCase(String text) {
        if (text == null || text.isBlank())
            return text;
        String[] words = text.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i].toLowerCase();
            if (i > 0 && (word.equals("to") || word.equals("in") || word.equals("of") || word.equals("and")
                    || word.equals("the"))) {
                sb.append(word);
            } else if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            if (i < words.length - 1)
                sb.append(" ");
        }
        return sb.toString();
    }

    private String normalizePhoneOrNull(String phone) {
        if (phone == null || phone.isBlank())
            return null;
        try {
            return TripService.normalizePhone(phone);
        } catch (Exception e) {
            return phone;
        }
    }

    private String generateAlphaNumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(REF_CHARS.charAt(RANDOM.nextInt(REF_CHARS.length())));
        }
        return sb.toString();
    }

    private String escapeHtml(String input) {
        if (input == null)
            return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    @Transactional
    public void simulateDeparture(SimulateDepartureRequest request, UserEntity currentUser) {
        if (request == null || request.getServiceDepartureId() == null || request.getServiceDepartureId().isBlank()) {
            throw new IllegalArgumentException("serviceDepartureId is required");
        }
        if (request.getState() == null || request.getState().isBlank()) {
            throw new IllegalArgumentException("state is required");
        }

        String state = request.getState().trim().toUpperCase();
        if (!state.equals("SCHEDULED") && !state.equals("ON_TIME") && !state.equals("DELAYED")
                && !state.equals("CANCELLED")) {
            throw new IllegalArgumentException(
                    "Invalid state: " + request.getState() + ". Expected SCHEDULED, ON_TIME, DELAYED, or CANCELLED");
        }

        int delay = request.getDelayMinutes() != null ? request.getDelayMinutes() : 0;
        if (delay < 0) {
            throw new IllegalArgumentException("delayMinutes cannot be negative");
        }
        if (state.equals("DELAYED") && delay <= 0) {
            throw new IllegalArgumentException("delayMinutes must be greater than 0 for DELAYED state");
        }

        ServiceDepartureEntity departure = departureRepo.findById(request.getServiceDepartureId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Departure not found: " + request.getServiceDepartureId()));

        // Addition 1: Caller must hold a booking on that departure (or demo flag)
        boolean hasBooking = currentUser != null
                && bookingRepo.existsByUserIdAndDepartureId(currentUser.getId(), departure.getId());
        if (!hasBooking && currentUser != null) {
            List<BookingEntity> depBookings = bookingRepo.findByDepartureId(departure.getId());
            hasBooking = depBookings.stream().anyMatch(b -> b.getTrip() != null && b.getTrip().getUser() != null
                    && b.getTrip().getUser().getId().equals(currentUser.getId()));
        }
        boolean allowAny = Boolean.getBoolean("market.simulation.allow-any")
                || "true".equalsIgnoreCase(System.getProperty("market.simulation.allow-any"));
        if (!hasBooking && !allowAny) {
            throw new AccessDeniedException(
                    "Access denied: You can only simulate departures on which you hold a booking");
        }

        departure.setStatus(state.equals("ON_TIME") ? "SCHEDULED" : state);
        departure.setDelayMinutes(state.equals("DELAYED") ? delay : 0);
        departureRepo.save(departure);

        List<BookingEntity> bookings = bookingRepo.findByDepartureId(departure.getId());
        if (simulatedStatusProvider != null) {
            for (BookingEntity b : bookings) {
                String bTripId = b.getTrip() != null ? b.getTrip().getId() : null;
                simulatedStatusProvider.clearScriptedStatus(bTripId, b.getId());
                simulatedStatusProvider.clearScriptedStatus(b.getId());
            }
        }
        Set<String> affectedTripIds = bookings.stream()
                .filter(b -> b.getTrip() != null)
                .map(b -> b.getTrip().getId())
                .collect(Collectors.toSet());

        if (statusCheckService != null) {
            for (String tripId : affectedTripIds) {
                try {
                    statusCheckService.checkTripNow(tripId, true);
                } catch (Exception e) {
                    log.error("Failed to run status check on trip {} after departure simulation: {}", tripId,
                            e.getMessage());
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public ReplacementPreviewResponse previewReplacement(String tripId, String oldBookingId, String serviceDepartureId,
            UserEntity currentUser) {
        TripEntity trip = tripRepo.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));
        if (trip.getUser() != null && currentUser != null && !trip.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Access denied: You do not own this trip");
        }

        BookingEntity oldBooking = bookingRepo.findById(oldBookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + oldBookingId));

        ServiceDepartureEntity departure = departureRepo.findById(serviceDepartureId)
                .orElseThrow(() -> new ResourceNotFoundException("Departure not found: " + serviceDepartureId));

        ServiceEntity service = departure.getService();

        List<String> passengerNamesList = new ArrayList<>();
        if (oldBooking.getPassengerNames() != null && !oldBooking.getPassengerNames().isBlank()) {
            passengerNamesList = Arrays.stream(oldBooking.getPassengerNames().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        int requestedSeats = passengerNamesList.isEmpty() ? 1 : passengerNamesList.size();
        if (oldBooking.getSeatNumbers() != null && !oldBooking.getSeatNumbers().isBlank()) {
            int oldSeatCount = oldBooking.getSeatNumbers().split(",").length;
            if (oldSeatCount > requestedSeats) {
                requestedSeats = oldSeatCount;
            }
        }

        List<SeatHoldEntity> existingHolds = seatHoldRepo.findByDepartureId(departure.getId());
        Set<String> takenSeats = existingHolds.stream()
                .map(SeatHoldEntity::getSeatNumber)
                .collect(Collectors.toSet());

        List<String> autoAssigned = new ArrayList<>();
        for (int i = 1; i <= service.getSeatsTotal() && autoAssigned.size() < requestedSeats; i++) {
            String seat = String.valueOf(i);
            if (!takenSeats.contains(seat)) {
                autoAssigned.add(seat);
            }
        }

        BigDecimal unitPrice = service.getPrice();
        BigDecimal totalCost = unitPrice.multiply(BigDecimal.valueOf(requestedSeats));
        BigDecimal refundAmount = oldBooking.getCost() != null ? oldBooking.getCost() : BigDecimal.ZERO;
        BigDecimal netCost = totalCost.subtract(refundAmount);

        boolean isGenerated = "GENERATED".equalsIgnoreCase(service.getSource());
        String scheduleLabel = isGenerated ? "Generated schedule, not an official timetable" : "Official schedule";

        LocalDate depDate = departure.getTravelDate();
        LocalDate arrDate = Boolean.TRUE.equals(service.getArrivesNextDay()) ? depDate.plusDays(1) : depDate;
        LocalDateTime depTime = depDate.atTime(service.getDepartureTime());
        LocalDateTime arrTime = arrDate.atTime(service.getArrivalTime());

        return ReplacementPreviewResponse.builder()
                .departureId(departure.getId())
                .operator(service.getOperator() != null ? service.getOperator().getName() : "Odyssey Partner")
                .vehicleNumber(service.getVehicleNumber())
                .mode(service.getMode())
                .origin(service.getOriginCity())
                .destination(service.getDestinationCity())
                .originCity(service.getOriginCity())
                .destinationCity(service.getDestinationCity())
                .departureTime(depTime)
                .arrivalTime(arrTime)
                .seatsAvailable(departure.getSeatsAvailable())
                .seatsLeft(departure.getSeatsAvailable())
                .passengerCount(requestedSeats)
                .passengers(passengerNamesList.isEmpty() ? List.of(
                        currentUser != null && currentUser.getName() != null ? currentUser.getName() : "Passenger 1")
                        : passengerNamesList)
                .assignedSeats(autoAssigned)
                .unitPrice(unitPrice)
                .totalCost(totalCost)
                .replacementCost(totalCost)
                .refundAmount(refundAmount)
                .netCost(netCost)
                .scheduleLabel(scheduleLabel)
                .disclaimer(DEMO_DISCLAIMER)
                .build();
    }

    @Transactional
    public ReplacementBookingResponse bookReplacement(ReplacementBookingRequest request, UserEntity currentUser) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        TripEntity trip = tripRepo.findById(request.getTripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + request.getTripId()));

        if (trip.getUser() != null && currentUser != null && !trip.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Access denied: You do not own this trip");
        }

        BookingEntity oldBooking = bookingRepo.findById(request.getOldBookingId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Old booking not found: " + request.getOldBookingId()));

        if (oldBooking.getTrip() != null && !oldBooking.getTrip().getId().equals(trip.getId())) {
            throw new IllegalArgumentException("Disrupted booking does not belong to the specified trip");
        }

        if (request.getServiceDepartureId().equals(oldBooking.getDepartureId())) {
            throw new ConflictException("Cannot book the same departure that was disrupted");
        }

        if ("CANCELLED".equalsIgnoreCase(oldBooking.getStatus())
                && "DEMO_REFUNDED".equalsIgnoreCase(oldBooking.getPaymentStatus())) {
            throw new ConflictException("Old booking has already been cancelled and refunded");
        }

        // Addition 3: Lock departure rows in ascending id order in the replacement
        // transaction to prevent deadlocks
        List<String> depIdsToLock = Stream.of(request.getServiceDepartureId(), oldBooking.getDepartureId())
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        Map<String, ServiceDepartureEntity> lockedDepartures = new HashMap<>();
        for (String depId : depIdsToLock) {
            ServiceDepartureEntity locked = departureRepo.findByIdForUpdate(depId)
                    .orElseThrow(() -> new ResourceNotFoundException("Departure not found: " + depId));
            lockedDepartures.put(depId, locked);
        }

        ServiceDepartureEntity newDeparture = lockedDepartures.get(request.getServiceDepartureId());
        ServiceEntity newService = newDeparture.getService();

        if ("CANCELLED".equalsIgnoreCase(newDeparture.getStatus())) {
            throw new ConflictException("Selected replacement departure is cancelled");
        }

        LocalDate depDate = newDeparture.getTravelDate();
        LocalDate arrDate = Boolean.TRUE.equals(newService.getArrivesNextDay()) ? depDate.plusDays(1) : depDate;
        LocalDateTime startTime = depDate.atTime(newService.getDepartureTime());
        LocalDateTime endTime = arrDate.atTime(newService.getArrivalTime());

        // Addition 4: Reject departure before disruption time or after mustArriveBy
        LocalDateTime disruptionTime = null;
        if (trip.getDisruptions() != null) {
            disruptionTime = trip.getDisruptions().stream()
                    .filter(d -> d.getBookingId() != null && oldBooking.getId().equals(d.getBookingId()))
                    .map(DisruptionEntity::getDisruptionTime)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            if (disruptionTime == null) {
                disruptionTime = trip.getDisruptions().stream()
                        .map(DisruptionEntity::getDisruptionTime)
                        .filter(Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElse(null);
            }
        }
        if (disruptionTime != null && startTime.isBefore(disruptionTime)) {
            throw new ConflictException(
                    String.format("Replacement departure departs at %s, before disruption occurred at %s", startTime,
                            disruptionTime));
        }

        if (trip.getConstraints() != null && trip.getConstraints().getMustArriveBy() != null) {
            LocalDateTime deadline = trip.getConstraints().getMustArriveBy();
            if (endTime.isAfter(deadline)) {
                throw new ConflictException(
                        String.format("Replacement departure arrives at %s, which breaches the trip deadline of %s",
                                endTime, deadline));
            }
        }

        // Addition 4: Keep passenger count from old booking, auto-assign first free
        // seats
        List<String> passengerNamesList = new ArrayList<>();
        if (request.getPassengers() != null && !request.getPassengers().isEmpty()) {
            for (PassengerDto p : request.getPassengers()) {
                passengerNamesList.add(p.getName() + (p.getAge() != null ? " (" + p.getAge() + ")" : ""));
            }
        } else if (oldBooking.getPassengerNames() != null && !oldBooking.getPassengerNames().isBlank()) {
            passengerNamesList = Arrays.stream(oldBooking.getPassengerNames().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        if (passengerNamesList.isEmpty()) {
            passengerNamesList
                    .add(currentUser != null && currentUser.getName() != null ? currentUser.getName() : "Passenger 1");
        }

        int requestedSeats = passengerNamesList.size();
        if (oldBooking.getSeatNumbers() != null && !oldBooking.getSeatNumbers().isBlank()) {
            int oldSeatCount = oldBooking.getSeatNumbers().split(",").length;
            if (oldSeatCount > requestedSeats) {
                requestedSeats = oldSeatCount;
            }
        }

        if (newDeparture.getSeatsAvailable() < requestedSeats) {
            throw new ConflictException("Not enough seats available on replacement departure (sold out)");
        }

        List<String> resolvedSeats = new ArrayList<>();
        List<SeatHoldEntity> existingHolds = seatHoldRepo.findByDepartureId(newDeparture.getId());
        Set<String> takenSeats = existingHolds.stream()
                .map(SeatHoldEntity::getSeatNumber)
                .collect(Collectors.toSet());

        if (request.getSeatNumbers() != null && !request.getSeatNumbers().isEmpty()) {
            if (request.getSeatNumbers().size() != requestedSeats) {
                throw new IllegalArgumentException("Selected seat count (" + request.getSeatNumbers().size()
                        + ") does not match passenger count (" + requestedSeats + ")");
            }
            for (String seat : request.getSeatNumbers()) {
                if (takenSeats.contains(seat.trim())) {
                    throw new ConflictException("Seat " + seat + " is already taken");
                }
                resolvedSeats.add(seat.trim());
            }
        } else {
            for (int i = 1; i <= newService.getSeatsTotal() && resolvedSeats.size() < requestedSeats; i++) {
                String seat = String.valueOf(i);
                if (!takenSeats.contains(seat)) {
                    resolvedSeats.add(seat);
                }
            }
            if (resolvedSeats.size() < requestedSeats) {
                throw new ConflictException("Not enough contiguous free seats available to complete booking");
            }
        }

        // Release old seat holds if old booking held seats
        List<SeatHoldEntity> oldHolds = seatHoldRepo.findByBookingId(oldBooking.getId());
        if (!oldHolds.isEmpty()) {
            seatHoldRepo.deleteAll(oldHolds);
            if (oldBooking.getDepartureId() != null) {
                ServiceDepartureEntity oldDep = lockedDepartures.get(oldBooking.getDepartureId());
                if (oldDep != null) {
                    oldDep.setSeatsAvailable(oldDep.getSeatsAvailable() + oldHolds.size());
                    departureRepo.save(oldDep);
                }
            }
        }

        newDeparture.setSeatsAvailable(newDeparture.getSeatsAvailable() - requestedSeats);
        departureRepo.save(newDeparture);

        // Addition 2: Old booking after replacement: status CANCELLED, payment_status
        // DEMO_REFUNDED
        BigDecimal refundAmount = oldBooking.getCost() != null ? oldBooking.getCost() : BigDecimal.ZERO;
        oldBooking.setStatus("CANCELLED");
        oldBooking.setPaymentStatus("DEMO_REFUNDED");
        bookingRepo.save(oldBooking);

        BigDecimal totalCost = newService.getPrice().multiply(BigDecimal.valueOf(requestedSeats));

        String newBookingId = IdGenerator.generate("BKG");
        String bookingRef = "ODY-" + generateAlphaNumeric(6);

        String seatNumbersJoined = String.join(", ", resolvedSeats);
        String passengerSummary = String.join(", ", passengerNamesList);

        BookingEntity newBooking = BookingEntity.builder()
                .id(newBookingId)
                .trip(trip)
                .user(currentUser)
                .type(newService.getMode())
                .provider(newService.getOperator() != null ? newService.getOperator().getName() : "Odyssey Transport")
                .fromLocation(newService.getOriginCity() + " (" + newService.getOriginStop() + ")")
                .toLocation(newService.getDestinationCity() + " (" + newService.getDestinationStop() + ")")
                .startTime(startTime)
                .endTime(endTime)
                .cost(totalCost)
                .status("CONFIRMED")
                .paymentStatus("DEMO_PAID")
                .source("MARKETPLACE")
                .bookingReference(bookingRef)
                .serviceId(newService.getId())
                .departureId(newDeparture.getId())
                .providerId(
                        newService.getProviderId() != null ? newService.getProviderId() : newDeparture.getProviderId())
                .vehicleNumber(newService.getVehicleNumber())
                .operator(newService.getOperator() != null ? newService.getOperator().getName() : "Odyssey Partner")
                .seatNumbers(seatNumbersJoined)
                .passengerNames(passengerSummary)
                .lastStatusState("ON_TIME")
                .lastDelayMinutes(0)
                .lastStatusSource("SIMULATED")
                .lastCheckedAt(LocalDateTime.now())
                .build();

        newBooking = bookingRepo.save(newBooking);

        for (String seat : resolvedSeats) {
            SeatHoldEntity hold = SeatHoldEntity.builder()
                    .id(IdGenerator.generate("HLD"))
                    .departure(newDeparture)
                    .bookingId(newBooking.getId())
                    .seatNumber(seat)
                    .status("CONFIRMED")
                    .createdAt(LocalDateTime.now())
                    .build();
            seatHoldRepo.save(hold);
        }

        RecoveryPlanEntity latestPlan = null;
        if (trip.getRecoveryPlans() != null && !trip.getRecoveryPlans().isEmpty()) {
            latestPlan = trip.getRecoveryPlans().stream()
                    .max(Comparator.comparing(RecoveryPlanEntity::getCreatedAt))
                    .orElse(null);
        }
        final String planNewLegId = latestPlan != null ? latestPlan.getNewLegId() : null;

        Set<String> replacedOrRemovedIds = new HashSet<>();
        replacedOrRemovedIds.add(oldBooking.getId());

        List<BookingEntity> toRemove = trip.getBookings().stream()
                .filter(b -> (b.getStatus().equalsIgnoreCase("PROPOSED") || b.getStatus().equalsIgnoreCase("REBOOKED"))
                        && (b.getType().equalsIgnoreCase(newService.getMode())
                                || (planNewLegId != null && b.getId().equals(planNewLegId))))
                .collect(Collectors.toList());
        for (BookingEntity rem : toRemove) {
            replacedOrRemovedIds.add(rem.getId());
            trip.getBookings().remove(rem);
            bookingRepo.delete(rem);
        }
        trip.getBookings().add(newBooking);

        if (latestPlan != null) {
            latestPlan.setNewLegId(newBookingId);
            updatePlanWithMarketplaceBooking(latestPlan, totalCost, refundAmount);
        }

        calculateDependsOnAndLinkOthers(newBooking, trip);

        Set<String> validBookingIds = trip.getBookings().stream()
                .map(BookingEntity::getId)
                .collect(Collectors.toSet());

        for (BookingEntity b : trip.getBookings()) {
            if (b.getDependsOn() != null && !b.getDependsOn().isEmpty()) {
                List<String> updatedDepends = new ArrayList<>();
                for (String depId : b.getDependsOn()) {
                    String resolvedId = replacedOrRemovedIds.contains(depId) ? newBookingId : depId;
                    if (validBookingIds.contains(resolvedId) && !resolvedId.equals(b.getId())
                            && !updatedDepends.contains(resolvedId)) {
                        updatedDepends.add(resolvedId);
                    }
                }
                b.setDependsOn(updatedDepends);
                bookingRepo.save(b);
            }
        }

        trip.getBookings().sort(Comparator.comparing(BookingEntity::getStartTime));

        if (notificationService != null) {
            try {
                com.odyssey.model.Trip tripModel = tripEntityMapper != null ? tripEntityMapper.toModel(trip)
                        : com.odyssey.model.Trip.builder().id(trip.getId()).title(trip.getTitle()).build();
                notificationService.sendReplacementBookedAlert(tripModel, newBooking, oldBooking, refundAmount);
            } catch (Exception e) {
                log.error("Failed to send replacement booked alert for trip {}: {}", trip.getId(),
                        e.getClass().getSimpleName());
            }
        }

        AuditEntryEntity audit = AuditEntryEntity.builder()
                .id(IdGenerator.generate("AUD"))
                .trip(trip)
                .timestamp(LocalDateTime.now())
                .actor("USER")
                .action("REPLACEMENT_BOOKED")
                .details(String.format(
                        "Replaced disrupted booking %s with marketplace booking %s (%s, %s %s) for Rs %s. Old booking refunded (DEMO_REFUNDED Rs %s). Demo payment DEMO_PAID.",
                        oldBooking.getId(), newBooking.getId(), bookingRef,
                        newService.getOperator() != null ? newService.getOperator().getName() : "Odyssey Partner",
                        newService.getVehicleNumber(), totalCost, refundAmount))
                .build();
        auditRepo.save(audit);

        tripRepo.save(trip);

        MarketplaceBookingDto dto = toDto(newBooking, null);

        return ReplacementBookingResponse.builder()
                .tripId(trip.getId())
                .oldBookingId(oldBooking.getId())
                .oldBookingStatus(oldBooking.getStatus())
                .oldPaymentStatus(oldBooking.getPaymentStatus())
                .newBooking(dto)
                .refundAmount(refundAmount)
                .replacementCost(totalCost)
                .netCost(totalCost.subtract(refundAmount))
                .assignedSeats(resolvedSeats)
                .disclaimer(DEMO_DISCLAIMER)
                .note("Demo marketplace: no real ticket is issued and no payment was charged.")
                .build();
    }

    private void updatePlanWithMarketplaceBooking(RecoveryPlanEntity plan, BigDecimal replacementCost,
            BigDecimal refundAmount) {
        try {
            if (plan.getCostBreakdownJson() != null) {
                CostBreakdown cb = objectMapper.readValue(plan.getCostBreakdownJson(), CostBreakdown.class);
                if (cb.getLines() != null) {
                    BigDecimal computedNet = BigDecimal.ZERO;
                    for (CostBreakdownLine line : cb.getLines()) {
                        if (line.getLabel() != null && (line.getLabel().toLowerCase().contains("new")
                                || line.getLabel().toLowerCase().contains("ticket"))) {
                            line.setAmount(replacementCost);
                            line.setAssumptionNote("Confirmed ticket fare for replacement journey");
                        } else if (line.isDeduction() && line.getLabel() != null
                                && line.getLabel().toLowerCase().contains("refund")) {
                            line.setAmount(refundAmount.negate());
                        }
                        computedNet = computedNet.add(line.getAmount());
                    }
                    cb.setNetTripCost(computedNet);
                    if (cb.getMaxBudget() != null) {
                        cb.setRemainingBudget(cb.getMaxBudget().subtract(computedNet));
                    }
                    plan.setNetTripCost(computedNet);
                    plan.setRemainingBudget(cb.getRemainingBudget());
                    plan.setCostBreakdownJson(objectMapper.writeValueAsString(cb));
                }
            }
            if (plan.getChecklistItems() != null) {
                for (PlanChecklistItemEntity item : plan.getChecklistItems()) {
                    boolean isReplacementBookAction = item
                            .getType() == com.odyssey.model.enums.RecoveryActionType.BOOK_REPLACEMENT
                            || (item.getType() == null && item.getText() != null
                                    && (item.getText().toLowerCase().contains("transport")
                                            || item.getText().toLowerCase().contains("leg")
                                            || item.getText().toLowerCase().contains("book")));
                    if (isReplacementBookAction) {
                        item.setDone(true);
                        item.setStatus(com.odyssey.model.enums.RecoveryActionStatus.DONE);
                        if (item.getCompletedAt() == null) {
                            item.setCompletedAt(LocalDateTime.now());
                        }
                    }
                }
            }
            if (ledgerService != null && plan.getTrip() != null && tripEntityMapper != null) {
                try {
                    ledgerService.syncPlanLedger(tripEntityMapper.toModel(plan.getTrip()),
                            tripEntityMapper.toRecoveryPlanModel(plan));
                } catch (Exception ex) {
                    log.warn("Could not sync ledger on marketplace booking update: {}", ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Could not update plan breakdown json: {}", e.getMessage());
        }
    }
}
