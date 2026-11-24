package com.odyssey.repository.jpa;

import com.odyssey.entity.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceJpaRepository extends JpaRepository<ServiceEntity, String> {
    List<ServiceEntity> findByOriginCityAndDestinationCityAndActiveTrue(String originCity, String destinationCity);
    List<ServiceEntity> findByOriginCityAndDestinationCityAndModeAndActiveTrue(String originCity, String destinationCity, String mode);
    List<ServiceEntity> findByActiveTrue();
    List<ServiceEntity> findByProviderId(String providerId);
    boolean existsByOriginCityAndDestinationCity(String originCity, String destinationCity);
    long countBySource(String source);
    long countBySourceAndMode(String source, String mode);
}
