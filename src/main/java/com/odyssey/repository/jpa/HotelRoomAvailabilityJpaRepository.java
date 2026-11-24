package com.odyssey.repository.jpa;

import com.odyssey.entity.HotelRoomAvailabilityEntity;
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
public interface HotelRoomAvailabilityJpaRepository extends JpaRepository<HotelRoomAvailabilityEntity, String> {

    List<HotelRoomAvailabilityEntity> findByRoomTypeIdAndStayDateBetweenOrderByStayDateAsc(String roomTypeId, LocalDate start, LocalDate end);

    Optional<HotelRoomAvailabilityEntity> findByRoomTypeIdAndStayDate(String roomTypeId, LocalDate stayDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM HotelRoomAvailabilityEntity a WHERE a.roomTypeId = :roomTypeId AND a.stayDate = :stayDate")
    Optional<HotelRoomAvailabilityEntity> findForUpdate(@Param("roomTypeId") String roomTypeId, @Param("stayDate") LocalDate stayDate);

    List<HotelRoomAvailabilityEntity> findByHotelIdAndStayDate(String hotelId, LocalDate stayDate);

    List<HotelRoomAvailabilityEntity> findByRoomTypeIdAndStayDateGreaterThanEqual(String roomTypeId, LocalDate stayDate);

    List<HotelRoomAvailabilityEntity> findByHotelIdAndStayDateGreaterThanEqual(String hotelId, LocalDate stayDate);
}
