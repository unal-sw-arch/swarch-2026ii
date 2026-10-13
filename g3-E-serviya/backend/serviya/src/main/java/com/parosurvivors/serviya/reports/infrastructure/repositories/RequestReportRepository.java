package com.parosurvivors.serviya.reports.infrastructure.repositories;

import com.parosurvivors.serviya.reports.infrastructure.entities.RequestReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RequestReportRepository extends JpaRepository<RequestReportEntity, Long> {
    Optional<RequestReportEntity> findByReportId(Long reportId);
}
