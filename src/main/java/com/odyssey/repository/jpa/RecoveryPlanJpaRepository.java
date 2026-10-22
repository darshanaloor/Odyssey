package com.odyssey.repository.jpa;

import com.odyssey.entity.RecoveryPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecoveryPlanJpaRepository extends JpaRepository<RecoveryPlanEntity, String> {
    List<RecoveryPlanEntity> findByTripIdOrderByCreatedAtDesc(String tripId);
    Optional<RecoveryPlanEntity> findFirstByTripIdOrderByCreatedAtDesc(String tripId);
    void deleteByTripId(String tripId);
}
