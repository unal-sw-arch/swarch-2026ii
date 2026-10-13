package com.parosurvivors.serviya.reports.application.ports.output;

import com.parosurvivors.serviya.reports.domain.ReportAction;

import java.util.List;
import java.util.Optional;

public interface ReportActionPersistencePort {
    ReportAction save(ReportAction action);
    Optional<ReportAction> findById(Long id);
    List<ReportAction> findByReportId(Long reportId);
}
