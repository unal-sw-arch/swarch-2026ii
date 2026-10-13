package com.parosurvivors.serviya.metrics.infrastructure.repositories;

import com.parosurvivors.serviya.metrics.infrastructure.entities.ClientMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientMetricsRepository extends JpaRepository<ClientMetricsEntity, Long> {
    Optional<ClientMetricsEntity> findByClientId(Long clientId);
}
