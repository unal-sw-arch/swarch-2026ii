package com.parosurvivors.serviya.profiles.infrastructure.repositories;

import com.parosurvivors.serviya.profiles.infrastructure.entities.OffererProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OffererProfileRepository extends JpaRepository<OffererProfileEntity, Long> {
    Optional<OffererProfileEntity> findByUserId(Long userId);
}
