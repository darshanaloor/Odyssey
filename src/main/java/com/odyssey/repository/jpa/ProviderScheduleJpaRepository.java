package com.odyssey.repository.jpa;

import com.odyssey.entity.ProviderScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderScheduleJpaRepository extends JpaRepository<ProviderScheduleEntity, String> {
    List<ProviderScheduleEntity> findByProviderIdOrderByCreatedAtDesc(String providerId);
    List<ProviderScheduleEntity> findByVehicleIdAndActiveTrue(String vehicleId);
    Optional<ProviderScheduleEntity> findByIdAndProviderId(String id, String providerId);
    long countByProviderId(String providerId);
    List<ProviderScheduleEntity> findByActiveTrue();
}
