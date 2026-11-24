package com.odyssey.repository.jpa;

import com.odyssey.entity.CabServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CabServiceJpaRepository extends JpaRepository<CabServiceEntity, String> {
    List<CabServiceEntity> findByCityIgnoreCase(String city);
    boolean existsByCityIgnoreCase(String city);
    List<CabServiceEntity> findByProviderIdOrderByCreatedAtDesc(String providerId);
    java.util.Optional<CabServiceEntity> findByIdAndProviderId(String id, String providerId);
    long countByProviderId(String providerId);
    List<CabServiceEntity> findByCityIgnoreCaseAndActiveTrue(String city);
}
