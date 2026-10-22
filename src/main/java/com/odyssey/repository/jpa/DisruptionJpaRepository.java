package com.odyssey.repository.jpa;

import com.odyssey.entity.DisruptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisruptionJpaRepository extends JpaRepository<DisruptionEntity, String> {
    List<DisruptionEntity> findByTripIdOrderByCreatedAtDesc(String tripId);
    void deleteByTripId(String tripId);
}
