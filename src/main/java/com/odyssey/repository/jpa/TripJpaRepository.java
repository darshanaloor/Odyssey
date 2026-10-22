package com.odyssey.repository.jpa;

import com.odyssey.entity.TripEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface TripJpaRepository extends JpaRepository<TripEntity, String> {
    List<TripEntity> findAllByOrderByCreatedAtDesc();

    @Query("SELECT t FROM TripEntity t WHERE t.userId = :userId OR t.id = 'TRIP-DEMO-001' ORDER BY t.createdAt DESC")
    List<TripEntity> findVisibleTripsForUser(@Param("userId") String userId);
}
