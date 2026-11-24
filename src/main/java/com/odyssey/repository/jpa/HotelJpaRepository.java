package com.odyssey.repository.jpa;

import com.odyssey.entity.HotelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HotelJpaRepository extends JpaRepository<HotelEntity, String> {
    List<HotelEntity> findByCityIgnoreCase(String city);
    boolean existsByCityIgnoreCase(String city);
    List<HotelEntity> findByProviderIdOrderByCreatedAtDesc(String providerId);
    java.util.Optional<HotelEntity> findByIdAndProviderId(String id, String providerId);
    long countByProviderId(String providerId);
    List<HotelEntity> findByCityIgnoreCaseAndActiveTrue(String city);
}
