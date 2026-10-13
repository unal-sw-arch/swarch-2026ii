package com.parosurvivors.serviya.metrics.infrastructure.repositories;

import com.parosurvivors.serviya.metrics.infrastructure.entities.ServiceTagMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceTagMetricsRepository extends JpaRepository<ServiceTagMetricsEntity, Long> {
    List<ServiceTagMetricsEntity> findByServiceId(Long serviceId);
    Optional<ServiceTagMetricsEntity> findByServiceIdAndTagId(Long serviceId, Long tagId);
}
