package com.odyssey.repository.jpa;

import com.odyssey.entity.PlanChecklistItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanChecklistItemJpaRepository extends JpaRepository<PlanChecklistItemEntity, String> {

    List<PlanChecklistItemEntity> findByRecoveryPlanIdOrderByItemOrderAsc(String planId);

    Optional<PlanChecklistItemEntity> findByIdAndRecoveryPlanId(String id, String planId);

    List<PlanChecklistItemEntity> findByTripIdOrderByItemOrderAsc(String tripId);
}

