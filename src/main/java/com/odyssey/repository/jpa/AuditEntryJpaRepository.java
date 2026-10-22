package com.odyssey.repository.jpa;

import com.odyssey.entity.AuditEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditEntryJpaRepository extends JpaRepository<AuditEntryEntity, String> {
    List<AuditEntryEntity> findByTripIdOrderByTimestampAsc(String tripId);
    void deleteByTripId(String tripId);
}
