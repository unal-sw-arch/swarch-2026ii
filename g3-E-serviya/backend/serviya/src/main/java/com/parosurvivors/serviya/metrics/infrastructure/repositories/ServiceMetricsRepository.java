package com.parosurvivors.serviya.metrics.infrastructure.repositories;

import com.parosurvivors.serviya.metrics.infrastructure.entities.ServiceMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceMetricsRepository extends JpaRepository<ServiceMetricsEntity, Long> {
    Optional<ServiceMetricsEntity> findByServiceId(Long serviceId);
    List<ServiceMetricsEntity> findByServiceIdIn(Collection<Long> serviceIds);
}
