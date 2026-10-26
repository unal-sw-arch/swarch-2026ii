package com.parosurvivors.serviya.reports.application.ports.output;

import com.parosurvivors.serviya.reports.domain.RequestReport;

import java.util.Optional;

public interface RequestReportPersistencePort {
    RequestReport save(RequestReport report);
    Optional<RequestReport> findById(Long id);
    Optional<RequestReport> findByReportId(Long reportId);
}
