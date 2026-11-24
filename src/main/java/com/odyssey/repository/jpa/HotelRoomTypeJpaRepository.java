package com.odyssey.repository.jpa;

import com.odyssey.entity.HotelRoomTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HotelRoomTypeJpaRepository extends JpaRepository<HotelRoomTypeEntity, String> {
    List<HotelRoomTypeEntity> findByHotelIdOrderByCreatedAtAsc(String hotelId);
    List<HotelRoomTypeEntity> findByHotelIdAndActiveTrueOrderByPricePerNightAsc(String hotelId);
    Optional<HotelRoomTypeEntity> findByIdAndHotelId(String id, String hotelId);
    long countByHotelId(String hotelId);
    boolean existsByHotelIdAndNameIgnoreCase(String hotelId, String name);
    boolean existsByHotelIdAndNameIgnoreCaseAndIdNot(String hotelId, String name, String id);
}
