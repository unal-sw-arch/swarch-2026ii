package com.parosurvivors.serviya.metrics.infrastructure.repositories;

import com.parosurvivors.serviya.metrics.infrastructure.entities.OffererMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OffererMetricsRepository extends JpaRepository<OffererMetricsEntity, Long> {
    Optional<OffererMetricsEntity> findByOffererId(Long offererId);
}
