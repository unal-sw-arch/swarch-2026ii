package com.parosurvivors.serviya.metrics.infrastructure.repositories;

import com.parosurvivors.serviya.metrics.infrastructure.entities.ClientTagMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientTagMetricsRepository extends JpaRepository<ClientTagMetricsEntity, Long> {
    List<ClientTagMetricsEntity> findByClientId(Long clientId);
    Optional<ClientTagMetricsEntity> findByClientIdAndTagId(Long clientId, Long tagId);
}
