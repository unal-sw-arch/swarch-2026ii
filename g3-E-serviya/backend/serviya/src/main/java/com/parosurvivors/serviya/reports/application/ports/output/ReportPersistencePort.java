package com.parosurvivors.serviya.reports.application.ports.output;

import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.reports.domain.ReportStatus;
import com.parosurvivors.serviya.reports.domain.ReportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ReportPersistencePort {
    Report save(Report report);
    Report update(Report report);
    Optional<Report> findById(Long id);
    List<Report> findAll();
    List<Report> findByReporterId(Long reporterId);
    List<Report> findByReportedUserId(Long reportedUserId);
    List<Report> findByStatus(ReportStatus status);
    List<Report> findByReportType(ReportType reportType);
    long countByReporterId(Long reporterId);
    long countByReportedUserId(Long reportedUserId);

    Page<Report> findReports(String type, String category, String status, Pageable pageable);
}
