package com.odyssey.repository.jpa;

import com.odyssey.entity.ProviderEntity;
import com.odyssey.model.enums.ProviderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderJpaRepository extends JpaRepository<ProviderEntity, String> {
    Optional<ProviderEntity> findByUserId(String userId);
    boolean existsByUserId(String userId);
    List<ProviderEntity> findByStatusOrderByCreatedAtDesc(ProviderStatus status);
}
