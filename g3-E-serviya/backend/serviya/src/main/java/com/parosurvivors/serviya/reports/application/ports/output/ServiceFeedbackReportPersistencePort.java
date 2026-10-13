package com.parosurvivors.serviya.reports.application.ports.output;

import com.parosurvivors.serviya.reports.domain.ServiceFeedbackReport;

import java.util.Optional;

public interface ServiceFeedbackReportPersistencePort {
    ServiceFeedbackReport save(ServiceFeedbackReport report);
    Optional<ServiceFeedbackReport> findById(Long id);
    Optional<ServiceFeedbackReport> findByReportId(Long reportId);
}
