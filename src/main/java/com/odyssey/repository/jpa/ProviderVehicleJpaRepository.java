package com.odyssey.repository.jpa;

import com.odyssey.entity.ProviderVehicleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderVehicleJpaRepository extends JpaRepository<ProviderVehicleEntity, String> {
    List<ProviderVehicleEntity> findByProviderIdOrderByCreatedAtDesc(String providerId);
    Optional<ProviderVehicleEntity> findByIdAndProviderId(String id, String providerId);
    long countByProviderId(String providerId);
    boolean existsByProviderIdAndVehicleNumberIgnoreCase(String providerId, String vehicleNumber);
    boolean existsByProviderIdAndVehicleNumberIgnoreCaseAndIdNot(String providerId, String vehicleNumber, String id);
}
