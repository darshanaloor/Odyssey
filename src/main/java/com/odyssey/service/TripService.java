package com.odyssey.service;

import com.odyssey.dto.request.AddBookingRequest;
import com.odyssey.dto.request.CreateTripRequest;
import com.odyssey.dto.request.UpdateConstraintsRequest;
import com.odyssey.exception.ResourceNotFoundException;
import com.odyssey.model.AuditEntry;
import com.odyssey.model.Booking;
import com.odyssey.model.Trip;
import com.odyssey.model.enums.AuditActor;
import com.odyssey.model.enums.BookingStatus;
import com.odyssey.repository.TripRepository;
import com.odyssey.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final DependencyGraph dependencyGraph;

    @Autowired(required = false)
    private AuthService authService;

    public Trip createTrip(CreateTripRequest request) {
        String normalizedPhone = normalizePhone(request.getTravellerPhone());
        String validatedEmail = validateEmail(request.getTravellerEmail());

        com.odyssey.entity.UserEntity currentUser = authService != null ? authService.getCurrentUserEntity() : null;
        String userId = currentUser != null ? currentUser.getId() : null;

        Trip trip = Trip.builder()
                .id(IdGenerator.generate("TRIP"))
                .title(request.getTitle())
                .description(request.getDescription())
                .travellerName(request.getTravellerName() != null && !request.getTravellerName().isBlank() ? request.getTravellerName() : "Traveller")
                .travellerPhone(normalizedPhone)
                .travellerEmail(validatedEmail)
                .userId(userId)
                .needsDestinationCab(Boolean.TRUE.equals(request.getNeedsDestinationCab()))
                .destinationCabDropoff(request.getDestinationCabDropoff())
                .needsHotel(Boolean.TRUE.equals(request.getNeedsHotel()))
                .notifyEmail(request.getNotifyEmail() == null || request.getNotifyEmail())
                .notifySms(request.getNotifySms() == null || request.getNotifySms())
                .notifyWhatsapp(Boolean.TRUE.equals(request.getNotifyWhatsapp()))
                .status("PLANNED")
                .bookings(request.getBookings() != null ? new ArrayList<>(request.getBookings()) : new ArrayList<>())
                .constraints(request.getConstraints())
                .hardCommitments(request.getHardCommitments() != null ? new ArrayList<>(request.getHardCommitments()) : new ArrayList<>())
                .activeDisruptions(new ArrayList<>())
                .options(new ArrayList<>())
                .auditLog(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Assign IDs to bookings ensuring uniqueness per trip
        Map<String, String> bookingIdMap = new HashMap<>();
        for (int i = 0; i < trip.getBookings().size(); i++) {
            Booking booking = trip.getBookings().get(i);
            String oldId = booking.getId();
            String prefix = trip.getId() + "-";
            String newId;
            if (oldId == null || oldId.isBlank()) {
                newId = prefix + "BKG-" + (i + 1);
            } else if (!oldId.startsWith(prefix)) {
                newId = prefix + oldId;
            } else {
                newId = oldId;
            }
            if (oldId != null && !oldId.isBlank()) {
                bookingIdMap.put(oldId, newId);
            }
            booking.setId(newId);

            if (booking.getStatus() == null) {
                booking.setStatus(BookingStatus.CONFIRMED);
            }
            if (booking.getSource() == null) {
                booking.setSource("INVENTORY");
            }
            // A new trip must start with every booking ON_TIME
            if (booking.getLastStatusState() == null) {
                booking.setLastStatusState("ON_TIME");
            }
            if (booking.getLastDelayMinutes() == null) {
                booking.setLastDelayMinutes(0);
            }
            if (booking.getLastStatusSource() == null) {
                booking.setLastStatusSource("SIMULATED");
            }
            if (booking.getLastCheckedAt() == null) {
                booking.setLastCheckedAt(LocalDateTime.now());
            }
        }

        // Remap dependsOn
        for (Booking booking : trip.getBookings()) {
            if (booking.getDependsOn() != null && !booking.getDependsOn().isEmpty()) {
                List<String> remapped = booking.getDependsOn().stream()
                        .map(dep -> bookingIdMap.getOrDefault(dep, dep))
                        .collect(Collectors.toList());
                booking.setDependsOn(remapped);
            }
        }

        // Assign IDs to hard commitments
        if (trip.getHardCommitments() != null) {
            for (int i = 0; i < trip.getHardCommitments().size(); i++) {
                com.odyssey.model.HardCommitment hc = trip.getHardCommitments().get(i);
                if (hc.getId() == null || hc.getId().isBlank()) {
                    hc.setId("HC-" + trip.getId() + "-" + (i + 1));
                }
            }
        }

        validateTrip(trip);

        trip.getAuditLog().add(AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.USER)
                .action("TRIP_CREATED")
                .details("Created new trip: " + trip.getTitle())
                .build());

        return tripRepository.save(trip);
    }

    public List<Trip> getAllTrips() {
        com.odyssey.entity.UserEntity currentUser = authService != null ? authService.getCurrentUserEntity() : null;
        if (currentUser != null) {
            return tripRepository.findVisibleForUser(currentUser.getId());
        }
        return tripRepository.findAll();
    }

    public Trip getTripById(String id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + id));
    }

    public Trip updateTrip(String id, CreateTripRequest request) {
        Trip trip = getTripById(id);
        trip.setTitle(request.getTitle());
        trip.setDescription(request.getDescription());
        if (request.getTravellerName() != null) {
            trip.setTravellerName(request.getTravellerName());
        }
        if (request.getTravellerPhone() != null && !request.getTravellerPhone().isBlank()) {
            trip.setTravellerPhone(normalizePhone(request.getTravellerPhone()));
        }
        if (request.getTravellerEmail() != null) {
            trip.setTravellerEmail(validateEmail(request.getTravellerEmail()));
        }
        if (request.getHardCommitments() != null) {
            trip.setHardCommitments(new ArrayList<>(request.getHardCommitments()));
        }
        if (request.getConstraints() != null) {
            trip.setConstraints(request.getConstraints());
        }
        if (request.getNeedsDestinationCab() != null) {
            trip.setNeedsDestinationCab(request.getNeedsDestinationCab());
        }
        if (request.getDestinationCabDropoff() != null) {
            trip.setDestinationCabDropoff(request.getDestinationCabDropoff());
        }
        if (request.getNeedsHotel() != null) {
            trip.setNeedsHotel(request.getNeedsHotel());
        }
        if (request.getNotifyEmail() != null) {
            trip.setNotifyEmail(request.getNotifyEmail());
        }
        if (request.getNotifySms() != null) {
            trip.setNotifySms(request.getNotifySms());
        }
        if (request.getNotifyWhatsapp() != null) {
            trip.setNotifyWhatsapp(request.getNotifyWhatsapp());
        }
        validateTrip(trip);

        trip.getAuditLog().add(AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.USER)
                .action("TRIP_UPDATED")
                .details("Updated trip details for: " + trip.getTitle())
                .build());

        return tripRepository.save(trip);
    }

    public void deleteTrip(String id) {
        if (!tripRepository.existsById(id)) {
            throw new ResourceNotFoundException("Trip not found with ID: " + id);
        }
        tripRepository.deleteById(id);
    }

    public Trip addBooking(String tripId, AddBookingRequest request) {
        Trip trip = getTripById(tripId);

        Booking booking = Booking.builder()
                .id(IdGenerator.generate(tripId + "-BKG"))
                .type(request.getType())
                .provider(request.getProvider())
                .from(request.getFrom())
                .to(request.getTo())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .cost(request.getCost())
                .status(request.getStatus() != null ? request.getStatus() : BookingStatus.CONFIRMED)
                .source("INVENTORY")
                .dependsOn(request.getDependsOn() != null ? new ArrayList<>(request.getDependsOn()) : new ArrayList<>())
                .lastStatusState("ON_TIME")
                .lastDelayMinutes(0)
                .lastStatusSource("SIMULATED")
                .lastCheckedAt(LocalDateTime.now())
                .hotelContactEmail(request.getHotelContactEmail())
                .build();

        Set<String> validIds = trip.getBookings().stream().map(Booking::getId).collect(Collectors.toSet());
        validateBooking(booking, validIds);

        trip.getBookings().add(booking);

        if (dependencyGraph.hasCycle(trip.getBookings())) {
            trip.getBookings().remove(booking);
            throw new IllegalArgumentException("Dependency cycle detected in bookings");
        }

        trip.getBookings().sort(Comparator.comparing(Booking::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));

        trip.getAuditLog().add(AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.USER)
                .action("BOOKING_ADDED")
                .details(String.format("Added %s booking %s (%s)", booking.getType(), booking.getId(), booking.getProvider()))
                .build());

        return tripRepository.save(trip);
    }

    public Trip updateBooking(String tripId, String bookingId, AddBookingRequest request) {
        Trip trip = getTripById(tripId);

        Booking existing = trip.getBookings().stream()
                .filter(b -> b.getId().equalsIgnoreCase(bookingId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with ID: " + bookingId + " in trip " + tripId));

        Set<String> validIds = trip.getBookings().stream()
                .map(Booking::getId)
                .filter(id -> !id.equalsIgnoreCase(bookingId))
                .collect(Collectors.toSet());

        Booking updated = Booking.builder()
                .id(existing.getId())
                .type(request.getType())
                .provider(request.getProvider())
                .from(request.getFrom())
                .to(request.getTo())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .cost(request.getCost())
                .status(request.getStatus() != null ? request.getStatus() : existing.getStatus())
                .source(existing.getSource())
                .dependsOn(request.getDependsOn() != null ? new ArrayList<>(request.getDependsOn()) : new ArrayList<>())
                .hotelContactEmail(request.getHotelContactEmail() != null ? request.getHotelContactEmail() : existing.getHotelContactEmail())
                .build();

        validateBooking(updated, validIds);

        // Temporarily replace and check cycle
        int idx = trip.getBookings().indexOf(existing);
        trip.getBookings().set(idx, updated);

        if (dependencyGraph.hasCycle(trip.getBookings())) {
            trip.getBookings().set(idx, existing);
            throw new IllegalArgumentException("Dependency cycle detected in bookings");
        }

        trip.getBookings().sort(Comparator.comparing(Booking::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));

        trip.getAuditLog().add(AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.USER)
                .action("BOOKING_UPDATED")
                .details(String.format("Updated booking %s (%s)", updated.getId(), updated.getProvider()))
                .build());

        return tripRepository.save(trip);
    }

    public Trip deleteBooking(String tripId, String bookingId) {
        Trip trip = getTripById(tripId);

        boolean removed = trip.getBookings().removeIf(b -> b.getId().equalsIgnoreCase(bookingId));
        if (!removed) {
            throw new ResourceNotFoundException("Booking not found with ID: " + bookingId + " in trip " + tripId);
        }

        // Clean up any dependencies on the deleted booking
        for (Booking b : trip.getBookings()) {
            if (b.getDependsOn() != null) {
                b.getDependsOn().removeIf(id -> id.equalsIgnoreCase(bookingId));
            }
        }

        trip.getAuditLog().add(AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.USER)
                .action("BOOKING_REMOVED")
                .details("Removed booking ID: " + bookingId)
                .build());

        return tripRepository.save(trip);
    }

    public Trip updateConstraints(String tripId, UpdateConstraintsRequest request) {
        Trip trip = getTripById(tripId);
        trip.setConstraints(request.getConstraints());
        validateTrip(trip);

        trip.getAuditLog().add(AuditEntry.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor(AuditActor.USER)
                .action("CONSTRAINTS_UPDATED")
                .details("Updated trip scheduling & budget constraints")
                .build());

        return tripRepository.save(trip);
    }

    private void validateTrip(Trip trip) {
        if (trip.getTitle() == null || trip.getTitle().isBlank()) {
            throw new IllegalArgumentException("Trip title cannot be blank");
        }
        if (trip.getConstraints() != null && trip.getConstraints().getMaxBudget() != null) {
            if (trip.getConstraints().getMaxBudget().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Constraint maxBudget must be positive (> 0)");
            }
        }

        // Require at least one booking before saving
        if (trip.getBookings() == null || trip.getBookings().isEmpty()) {
            throw new IllegalArgumentException("At least one booking is required before saving a trip");
        }

        if (Boolean.TRUE.equals(trip.getNeedsDestinationCab())) {
            if (trip.getDestinationCabDropoff() == null || trip.getDestinationCabDropoff().isBlank()) {
                throw new IllegalArgumentException("Destination cab drop-off location is required when cab is requested");
            }
        }

        LocalDateTime earliestBookingStart = trip.getBookings().stream()
                .map(Booking::getStartTime)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        if (trip.getConstraints() != null && trip.getConstraints().getMustArriveBy() != null) {
            if (earliestBookingStart != null && !trip.getConstraints().getMustArriveBy().isAfter(earliestBookingStart)) {
                throw new IllegalArgumentException(String.format(
                        "Must arrive by time (%s) must be after the earliest booking departure time (%s)",
                        trip.getConstraints().getMustArriveBy(), earliestBookingStart));
            }
        }

        Set<String> bookingIds = trip.getBookings().stream()
                .map(Booking::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (Booking b : trip.getBookings()) {
            validateBooking(b, bookingIds);
        }

        if (dependencyGraph.hasCycle(trip.getBookings())) {
            throw new IllegalArgumentException("Dependency cycle detected in bookings");
        }
    }

    public static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Traveller phone number is required (10 digits)");
        }
        String clean = phone.replaceAll("[^0-9]", "");
        if (clean.length() == 12 && clean.startsWith("91")) {
            clean = clean.substring(2);
        } else if (clean.length() == 11 && clean.startsWith("0")) {
            clean = clean.substring(1);
        }
        if (clean.length() != 10) {
            throw new IllegalArgumentException("Traveller phone number must be exactly 10 digits. Provided: " + phone);
        }
        return "+91" + clean;
    }

    public static String validateEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String clean = email.trim();
        String emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        if (!clean.matches(emailRegex)) {
            throw new IllegalArgumentException("Invalid traveller email format: " + email);
        }
        return clean;
    }

    private void validateBooking(Booking b, Set<String> validIds) {
        if (b.getStartTime() == null || b.getEndTime() == null) {
            throw new IllegalArgumentException("Booking startTime and endTime are required");
        }
        if (!b.getEndTime().isAfter(b.getStartTime())) {
            throw new IllegalArgumentException("Booking endTime (" + b.getEndTime() + ") must be after startTime (" + b.getStartTime() + ")");
        }
        if (b.getCost() != null && b.getCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Booking cost cannot be negative");
        }
        if (b.getDependsOn() != null) {
            for (String dep : b.getDependsOn()) {
                if (dep != null && !dep.isBlank()) {
                    if (dep.equalsIgnoreCase(b.getId())) {
                        throw new IllegalArgumentException("Booking cannot depend on itself: " + dep);
                    }
                    if (validIds != null && !validIds.contains(dep)) {
                        throw new IllegalArgumentException("Unknown dependency booking ID: " + dep);
                    }
                }
            }
        }
    }
}
