package com.odyssey.repository.jpa;

import com.odyssey.entity.ServiceDepartureEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceDepartureJpaRepository extends JpaRepository<ServiceDepartureEntity, String> {

    Optional<ServiceDepartureEntity> findByServiceIdAndTravelDate(String serviceId, LocalDate travelDate);
    Optional<ServiceDepartureEntity> findByScheduleIdAndTravelDate(String scheduleId, LocalDate travelDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM ServiceDepartureEntity d WHERE d.id = :id")
    Optional<ServiceDepartureEntity> findByIdForUpdate(@Param("id") String id);

    List<ServiceDepartureEntity> findByTravelDate(LocalDate travelDate);

    @Query("SELECT d FROM ServiceDepartureEntity d WHERE UPPER(d.service.originCity) = UPPER(:origin) AND UPPER(d.service.destinationCity) = UPPER(:dest) AND d.travelDate = :date AND d.service.active = true")
    List<ServiceDepartureEntity> findDepartures(@Param("origin") String origin, @Param("dest") String dest, @Param("date") LocalDate date);

    @Query("SELECT d FROM ServiceDepartureEntity d WHERE UPPER(d.service.originCity) = UPPER(:origin) AND UPPER(d.service.destinationCity) = UPPER(:dest) AND UPPER(d.service.mode) = UPPER(:mode) AND d.travelDate = :date AND d.service.active = true")
    List<ServiceDepartureEntity> findDeparturesWithMode(@Param("origin") String origin, @Param("dest") String dest, @Param("mode") String mode, @Param("date") LocalDate date);

    @Query("SELECT d FROM ServiceDepartureEntity d WHERE (d.service.originCity = :origin OR d.service.originCity = :originName) AND (d.service.destinationCity = :dest OR d.service.destinationCity = :destName) AND d.travelDate BETWEEN :startDate AND :endDate AND d.service.active = true")
    List<ServiceDepartureEntity> findDeparturesBetweenDates(
            @Param("origin") String origin,
            @Param("originName") String originName,
            @Param("dest") String dest,
            @Param("destName") String destName,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    List<ServiceDepartureEntity> findByProviderIdOrderByTravelDateAsc(String providerId);

    List<ServiceDepartureEntity> findByScheduleIdOrderByTravelDateAsc(String scheduleId);

    List<ServiceDepartureEntity> findByScheduleIdAndTravelDateGreaterThanEqual(String scheduleId, LocalDate travelDate);

    Optional<ServiceDepartureEntity> findByIdAndProviderId(String id, String providerId);
}
