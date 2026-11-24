package com.odyssey.repository.jpa;

import com.odyssey.entity.TripLedgerEntryEntity;
import com.odyssey.model.enums.LedgerBasis;
import com.odyssey.model.enums.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripLedgerEntryJpaRepository extends JpaRepository<TripLedgerEntryEntity, String> {

    List<TripLedgerEntryEntity> findByTripIdOrderByCreatedAtAsc(String tripId);

    boolean existsByTripId(String tripId);

    List<TripLedgerEntryEntity> findByTripIdOrderByCreatedAtDesc(String tripId);

    List<TripLedgerEntryEntity> findByTripIdAndType(String tripId, LedgerEntryType type);

    List<TripLedgerEntryEntity> findByParentEntryId(String parentEntryId);

    List<TripLedgerEntryEntity> findByTripIdAndParentEntryId(String tripId, String parentEntryId);

    boolean existsByBookingIdAndBasis(String bookingId, LedgerBasis basis);

    List<TripLedgerEntryEntity> findByBookingId(String bookingId);

    void deleteByTripId(String tripId);
}

