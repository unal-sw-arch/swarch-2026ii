package com.parosurvivors.serviya.metrics.infrastructure.repositories;

import com.parosurvivors.serviya.metrics.infrastructure.entities.OffererTagMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OffererTagMetricsRepository extends JpaRepository<OffererTagMetricsEntity, Long> {
    List<OffererTagMetricsEntity> findByOffererId(Long offererId);
    Optional<OffererTagMetricsEntity> findByOffererIdAndTagId(Long offererId, Long tagId);
}
