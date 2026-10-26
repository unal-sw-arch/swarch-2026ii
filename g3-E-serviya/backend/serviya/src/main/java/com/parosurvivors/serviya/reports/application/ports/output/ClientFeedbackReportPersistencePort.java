package com.parosurvivors.serviya.reports.application.ports.output;

import com.parosurvivors.serviya.reports.domain.ClientFeedbackReport;

import java.util.Optional;

public interface ClientFeedbackReportPersistencePort {
    ClientFeedbackReport save(ClientFeedbackReport report);
    Optional<ClientFeedbackReport> findById(Long id);
    Optional<ClientFeedbackReport> findByReportId(Long reportId);
}
