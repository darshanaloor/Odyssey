package com.odyssey.repository.jpa;

import com.odyssey.entity.OperatorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OperatorJpaRepository extends JpaRepository<OperatorEntity, String> {
    Optional<OperatorEntity> findByName(String name);
}
