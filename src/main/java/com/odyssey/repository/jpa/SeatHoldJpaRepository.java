package com.odyssey.repository.jpa;

import com.odyssey.entity.SeatHoldEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface SeatHoldJpaRepository extends JpaRepository<SeatHoldEntity, String> {

    List<SeatHoldEntity> findByDepartureId(String departureId);

    List<SeatHoldEntity> findByDepartureIdAndSeatNumberIn(String departureId, Collection<String> seatNumbers);

    List<SeatHoldEntity> findByBookingId(String bookingId);

    void deleteByBookingId(String bookingId);

    List<SeatHoldEntity> findByStatusAndCreatedAtBefore(String status, LocalDateTime cutoff);
}
