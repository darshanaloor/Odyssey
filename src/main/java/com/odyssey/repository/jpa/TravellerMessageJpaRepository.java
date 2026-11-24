package com.odyssey.repository.jpa;

import com.odyssey.entity.TravellerMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TravellerMessageJpaRepository extends JpaRepository<TravellerMessageEntity, String> {

    List<TravellerMessageEntity> findByTripIdOrderByCreatedAtDesc(String tripId);

    long countByTripIdAndSentAtAfter(String tripId, LocalDateTime after);
}

