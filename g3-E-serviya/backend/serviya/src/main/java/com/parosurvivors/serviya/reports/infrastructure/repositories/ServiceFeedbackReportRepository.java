package com.parosurvivors.serviya.reports.infrastructure.repositories;

import com.parosurvivors.serviya.reports.infrastructure.entities.ServiceFeedbackReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceFeedbackReportRepository extends JpaRepository<ServiceFeedbackReportEntity, Long> {
    Optional<ServiceFeedbackReportEntity> findByReportId(Long reportId);
}
