package com.odyssey.repository.jpa;

import com.odyssey.entity.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingJpaRepository extends JpaRepository<BookingEntity, String> {
    List<BookingEntity> findByTripIdOrderByStartTimeAsc(String tripId);
    void deleteByTripId(String tripId);
}
