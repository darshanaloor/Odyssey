package com.odyssey.repository.jpa;

import com.odyssey.entity.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingJpaRepository extends JpaRepository<BookingEntity, String> {
    List<BookingEntity> findByTripIdOrderByStartTimeAsc(String tripId);
    void deleteByTripId(String tripId);
    List<BookingEntity> findByUserIdOrderByStartTimeDesc(String userId);
    java.util.Optional<BookingEntity> findByIdAndUserId(String id, String userId);
    java.util.Optional<BookingEntity> findByBookingReference(String bookingReference);
    List<BookingEntity> findByDepartureId(String departureId);
    long countByDepartureId(String departureId);
    long countByDepartureIdAndStatusNot(String departureId, String status);
    boolean existsByUserIdAndDepartureId(String userId, String departureId);
    long countByProviderId(String providerId);
    List<BookingEntity> findByProviderIdOrderByStartTimeDesc(String providerId);
    List<BookingEntity> findByProviderIdAndTypeOrderByStartTimeDesc(String providerId, String type);
    List<BookingEntity> findByProviderIdAndStatusOrderByStartTimeDesc(String providerId, String status);
    List<BookingEntity> findByStatusOrderByStartTimeDesc(String status);
    List<BookingEntity> findByStatusAndConfirmationExpiresAtBefore(String status, java.time.LocalDateTime now);
}
