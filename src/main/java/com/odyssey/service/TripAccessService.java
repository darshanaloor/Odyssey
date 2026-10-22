package com.odyssey.service;

import com.odyssey.entity.UserEntity;
import com.odyssey.exception.ResourceNotFoundException;
import com.odyssey.model.Trip;
import com.odyssey.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripAccessService {

    private final TripRepository tripRepository;
    private final AuthService authService;

    /**
     * Enforce that the currently authenticated user owns the trip for mutating operations.
     * TRIP-DEMO-001 is shared and unowned, so mutating operations on it return 403 for non-owners
     * (only /api/demo/reset is permitted to mutate it).
     */
    public Trip requireOwner(String tripId) {
        UserEntity currentUser = authService.getCurrentUserEntity();
        if (currentUser == null) {
            throw new BadCredentialsException("Authentication required");
        }

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));

        if (trip.getUserId() == null) {
            // Unowned/shared trip (such as TRIP-DEMO-001) cannot be mutated by regular users
            throw new AccessDeniedException("Access denied: shared demo trip cannot be modified. Use demo reset instead.");
        }

        if (!currentUser.getId().equals(trip.getUserId())) {
            throw new AccessDeniedException("Access denied: you do not have permission to modify this trip");
        }

        return trip;
    }

    /**
     * Enforce that the currently authenticated user has read access to the trip.
     * TRIP-DEMO-001 (or trips with user_id NULL) are readable by any authenticated user.
     */
    public Trip requireReadAccess(String tripId) {
        UserEntity currentUser = authService.getCurrentUserEntity();
        if (currentUser == null) {
            throw new BadCredentialsException("Authentication required");
        }

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));

        // TRIP-DEMO-001 is readable by any logged-in user
        if ("TRIP-DEMO-001".equalsIgnoreCase(trip.getId()) || trip.getUserId() == null) {
            return trip;
        }

        if (!currentUser.getId().equals(trip.getUserId())) {
            throw new AccessDeniedException("Access denied: you do not have permission to view this trip");
        }

        return trip;
    }
}
