package com.parosurvivors.serviya.reports.infrastructure.repositories;

import com.parosurvivors.serviya.reports.infrastructure.entities.ClientFeedbackReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientFeedbackReportRepository extends JpaRepository<ClientFeedbackReportEntity, Long> {
    Optional<ClientFeedbackReportEntity> findByReportId(Long reportId);
}
