package com.odyssey.repository.jpa;

import com.odyssey.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, String> {

    List<NotificationEntity> findByTripIdOrderByCreatedAtDesc(String tripId);

    boolean existsByTripIdAndDedupKey(String tripId, String dedupKey);

    long countByTripIdAndEventTypeAndCreatedAtAfter(String tripId, String eventType, LocalDateTime after);

    void deleteByTripId(String tripId);
}
