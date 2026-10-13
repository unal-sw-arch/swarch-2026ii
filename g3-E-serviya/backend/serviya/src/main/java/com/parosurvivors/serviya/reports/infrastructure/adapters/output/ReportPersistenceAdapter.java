package com.parosurvivors.serviya.reports.infrastructure.adapters.output;

import com.parosurvivors.serviya.reports.application.ports.output.ReportPersistencePort;
import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.reports.domain.ReportStatus;
import com.parosurvivors.serviya.reports.domain.ReportType;
import com.parosurvivors.serviya.reports.infrastructure.entities.ReportEntity;
import com.parosurvivors.serviya.reports.infrastructure.mappers.ReportPersistenceMapper;
import com.parosurvivors.serviya.reports.infrastructure.repositories.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ReportPersistenceAdapter implements ReportPersistencePort {

    private final ReportRepository repository;
    private final ReportPersistenceMapper mapper;

    @Override
    public Report save(Report report) {
        ReportEntity saved = repository.save(mapper.toEntity(report));
        return mapper.toDomain(saved);
    }

    @Override
    public Report update(Report report) {
        ReportEntity updated = repository.save(mapper.toEntity(report));
        return mapper.toDomain(updated);
    }

    @Override
    public Optional<Report> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Report> findAll() {
        return repository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Report> findByReporterId(Long reporterId) {
        return repository.findByReporterId(reporterId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Report> findByReportedUserId(Long reportedUserId) {
        return repository.findByReportedUserId(reportedUserId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Report> findByStatus(ReportStatus status) {
        return repository.findByStatus(status).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Report> findByReportType(ReportType reportType) {
        return repository.findByReportType(reportType).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long countByReporterId(Long reporterId) {
        return repository.countByReporterId(reporterId);
    }

    @Override
    public long countByReportedUserId(Long reportedUserId) {
        return repository.countByReportedUserId(reportedUserId);
    }

    @Override
    public Page<Report> findReports(String type, String category, String status, Pageable pageable) {
        ReportType reportType = (type == null || type.isBlank()) ? null : ReportType.valueOf(type.toUpperCase());
        ReportStatus reportStatus = (status == null || status.isBlank()) ? null : ReportStatus.valueOf(status.toUpperCase());
        String cat = (category == null || category.isBlank()) ? null : category;

        Page<ReportEntity> entityPage;
        if (reportType != null && cat != null && reportStatus != null) {
            entityPage = repository.findByReportTypeAndCategoryAndStatus(reportType, cat, reportStatus, pageable);
        } else if (reportType != null && cat != null) {
            entityPage = repository.findByReportTypeAndCategory(reportType, cat, pageable);
        } else if (reportType != null && reportStatus != null) {
            entityPage = repository.findByReportTypeAndStatus(reportType, reportStatus, pageable);
        } else if (cat != null && reportStatus != null) {
            entityPage = repository.findByCategoryAndStatus(cat, reportStatus, pageable);
        } else if (reportType != null) {
            entityPage = repository.findByReportType(reportType, pageable);
        } else if (cat != null) {
            entityPage = repository.findByCategory(cat, pageable);
        } else if (reportStatus != null) {
            entityPage = repository.findByStatus(reportStatus, pageable);
        } else {
            entityPage = repository.findAllReports(pageable);
        }

        return entityPage.map(mapper::toDomain);
    }
}
