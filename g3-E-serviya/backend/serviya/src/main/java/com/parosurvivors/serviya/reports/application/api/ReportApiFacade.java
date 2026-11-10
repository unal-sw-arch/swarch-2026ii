package com.parosurvivors.serviya.reports.application.api;

import com.parosurvivors.serviya.reports.api.ReportCommandApi;
import com.parosurvivors.serviya.reports.api.ReportQueryApi;
import com.parosurvivors.serviya.reports.api.dto.ReportActionSnapshot;
import com.parosurvivors.serviya.reports.api.dto.ReportSnapshot;
import com.parosurvivors.serviya.reports.api.dto.ReportSummarySnapshot;
import com.parosurvivors.serviya.reports.application.ports.input.ReportActionServicePort;
import com.parosurvivors.serviya.reports.application.ports.input.ReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.input.ServiceFeedbackReportServicePort;
import com.parosurvivors.serviya.reports.application.ports.input.ClientFeedbackReportServicePort;
import com.parosurvivors.serviya.reports.application.dto.command.CreateServiceFeedbackReportCommand;
import com.parosurvivors.serviya.reports.application.dto.command.CreateClientFeedbackReportCommand;
import com.parosurvivors.serviya.reports.application.ports.output.ReportActionPersistencePort;
import com.parosurvivors.serviya.reports.application.ports.output.ReportPersistencePort;
import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.reports.domain.ReportAction;
import com.parosurvivors.serviya.reports.domain.ReportActionType;
import com.parosurvivors.serviya.reports.domain.ReportSummary;
import com.parosurvivors.serviya.shared.contract.PageResult;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Implementacion de los contratos publicos de reports sobre los casos de uso existentes.
 */
@Component
@RequiredArgsConstructor
public class ReportApiFacade implements ReportQueryApi, ReportCommandApi {

    private final ReportPersistencePort reportPersistencePort;
    private final ReportActionPersistencePort reportActionPersistencePort;
    private final ReportServicePort reportServicePort;
    private final ReportActionServicePort reportActionServicePort;
    private final ServiceFeedbackReportServicePort serviceFeedbackReportServicePort;
    private final ClientFeedbackReportServicePort clientFeedbackReportServicePort;

    // ---------------------------------------------------------------------
    // ReportQueryApi
    // ---------------------------------------------------------------------

    @Override
    public Optional<ReportSnapshot> findById(Long reportId) {
        return reportPersistencePort.findById(reportId).map(this::toSnapshot);
    }

    @Override
    public Optional<ReportSummarySnapshot> findSummary(Long reportId) {
        try {
            return Optional.of(toSummarySnapshot(reportServicePort.getReportSummary(reportId)));
        } catch (ResourceNotFoundException ex) {
            return Optional.empty();
        }
    }

    @Override
    public PageResult<ReportSnapshot> search(String type, String category, String status, int page, int size) {
        Page<Report> result = reportPersistencePort.findReports(
                type, category, status, PageRequest.of(page, size));
        return new PageResult<>(
                result.getContent().stream().map(this::toSnapshot).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public List<ReportActionSnapshot> getActions(Long reportId) {
        return reportActionPersistencePort.findByReportId(reportId).stream()
                .map(this::toActionSnapshot)
                .toList();
    }

    @Override
    public long countByReportedUser(Long userId) {
        return reportPersistencePort.countByReportedUserId(userId);
    }

    @Override
    public long countByReporter(Long userId) {
        return reportPersistencePort.countByReporterId(userId);
    }

    // ---------------------------------------------------------------------
    // ReportCommandApi
    // ---------------------------------------------------------------------

    @Override
    public ReportSnapshot createBaseReport(
            Long reporterId, Long reportedUserId, String type, String category, String reason) {
        return toSnapshot(reportServicePort.createBaseReport(reporterId, reportedUserId, type, category, reason));
    }

    @Override
    public void resolveReport(Long reportId, Long adminId, String actionType) {
        reportServicePort.resolveReport(reportId, adminId, toActionType(actionType));
    }

    @Override
    public void closeReport(Long reportId, Long adminId) {
        reportServicePort.closeReport(reportId, adminId);
    }

    @Override
    public ReportActionSnapshot createAction(Long reportId, Long adminId, String actionType) {
        return toActionSnapshot(reportActionServicePort.createAction(
                reportId, adminId, toActionType(actionType)));
    }

    @Override
    public Long createServiceFeedbackReport(
            Long reporterId, Long reportedUserId, String category, String reason, Long feedbackId) {
        return serviceFeedbackReportServicePort.createReport(
                new CreateServiceFeedbackReportCommand(
                        reporterId, reportedUserId, category, reason, feedbackId))
                .getReportId();
    }

    @Override
    public Long createClientFeedbackReport(
            Long reporterId, Long reportedUserId, String category, String reason, Long feedbackId) {
        return clientFeedbackReportServicePort.createReport(
                new CreateClientFeedbackReportCommand(
                        reporterId, reportedUserId, category, reason, feedbackId))
                .getReportId();
    }

    // ---------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------

    private ReportSnapshot toSnapshot(Report report) {
        return new ReportSnapshot(
                report.getId(),
                report.getReporterId(),
                report.getReportedUserId(),
                report.getReportType() == null ? null : report.getReportType().name(),
                report.getCategory(),
                report.getReason(),
                report.getStatus() == null ? null : report.getStatus().name(),
                report.getPriority() == null ? null : report.getPriority().name(),
                report.getCreatedAt(),
                report.getUpdatedAt());
    }

    private ReportSummarySnapshot toSummarySnapshot(ReportSummary summary) {
        return new ReportSummarySnapshot(
                summary.id(),
                summary.reporterId(),
                summary.reportedUserId(),
                summary.reportType() == null ? null : summary.reportType().name(),
                summary.category(),
                summary.status() == null ? null : summary.status().name(),
                summary.requestId(),
                summary.serviceFeedbackId(),
                summary.clientFeedbackId());
    }

    private ReportActionSnapshot toActionSnapshot(ReportAction action) {
        return new ReportActionSnapshot(
                action.getId(),
                action.getReportId(),
                action.getAdminId(),
                action.getActionType() == null ? null : action.getActionType().name(),
                action.getActionDescription(),
                action.getCreatedAt());
    }

    private ReportActionType toActionType(String actionType) {
        if (actionType == null) {
            throw new IllegalArgumentException("actionType is required");
        }
        try {
            return ReportActionType.valueOf(actionType);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown report action type: " + actionType, ex);
        }
    }
}
