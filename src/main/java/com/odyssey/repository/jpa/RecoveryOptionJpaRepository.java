package com.odyssey.repository.jpa;

import com.odyssey.entity.RecoveryOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecoveryOptionJpaRepository extends JpaRepository<RecoveryOptionEntity, String> {
    List<RecoveryOptionEntity> findByTripIdOrderByScoreDesc(String tripId);
    void deleteByTripId(String tripId);
}
