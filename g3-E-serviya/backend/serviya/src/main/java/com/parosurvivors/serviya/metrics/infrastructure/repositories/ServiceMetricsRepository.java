package com.parosurvivors.serviya.metrics.infrastructure.repositories;

import com.parosurvivors.serviya.metrics.infrastructure.entities.ServiceMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceMetricsRepository extends JpaRepository<ServiceMetricsEntity, Long> {
    Optional<ServiceMetricsEntity> findByServiceId(Long serviceId);
    List<ServiceMetricsEntity> findByServiceIdIn(Collection<Long> serviceIds);

    @Query("SELECT m.serviceId FROM ServiceMetricsEntity m "
            + "WHERE (:minRating IS NULL OR m.averageRating >= :minRating) "
            + "AND (:maxRating IS NULL OR m.averageRating <= :maxRating)")
    List<Long> findServiceIdsByAverageRating(
            @Param("minRating") BigDecimal minRating,
            @Param("maxRating") BigDecimal maxRating);
}
