package com.parosurvivors.serviya.reports.application.services;

import com.parosurvivors.serviya.reports.application.ports.input.ReportActionServicePort;
import com.parosurvivors.serviya.reports.application.ports.output.ReportActionPersistencePort;
import com.parosurvivors.serviya.reports.application.ports.output.ReportPersistencePort;
import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.reports.domain.ReportAction;
import com.parosurvivors.serviya.reports.domain.ReportActionType;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trazabilidad de acciones administrativas sobre reportes. Las acciones son registros automáticos:
 * dado un {@link ReportActionType}, se genera una descripción preestablecida (snapshot inmutable con
 * a quién afectó, quién reportó, reporte origen y categoría) y se persiste. Los admins las consultan
 * por reporte en orden cronológico.
 */
@Component
@RequiredArgsConstructor
public class ReportActionService implements ReportActionServicePort {

    private final ReportActionPersistencePort reportActionPersistencePort;
    private final ReportPersistencePort reportPersistencePort;

    @Override
    @Transactional
    public ReportAction createAction(Long reportId, Long adminId, ReportActionType actionType) {
        Report report = reportPersistencePort.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Reporte no encontrado: " + reportId));

        ReportAction action = ReportAction.builder()
                .reportId(reportId)
                .adminId(adminId)
                .actionType(actionType)
                .actionDescription(actionType.describe(report, adminId))
                .createdAt(LocalDateTime.now())
                .build();

        return reportActionPersistencePort.save(action);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportAction> getActionsByReport(Long reportId) {
        return reportActionPersistencePort.findByReportId(reportId).stream()
                .sorted(Comparator.comparing(ReportAction::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
}
