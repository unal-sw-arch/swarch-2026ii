package com.parosurvivors.serviya.reports.application.ports.input;

import com.parosurvivors.serviya.reports.application.dto.result.ReportDetailResult;
import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.reports.domain.ReportActionType;
import com.parosurvivors.serviya.reports.domain.ReportSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Puerto de entrada de ReportService — consultas y lógica base compartida (por composición).
 * Las lecturas devuelven dominio (Report) o el Result paraguas del detalle. Nunca tipos web.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 7).
 */
public interface ReportServicePort {

    Report createBaseReport(Long reporterId, Long reportedUserId, String type, String category, String reason);

    ReportDetailResult getReportDetail(Long reportId);

    /**
     * Vista reducida (base + id de la entidad objetivo) para orquestación interna de moderación. No enriquece
     * con perfiles ni con el detalle de solicitud/feedback: úsese cuando solo se necesita el reporte y su objetivo.
     */
    ReportSummary getReportSummary(Long reportId);

    Page<Report> getReports(String type, String category, String status, Pageable pageable);

    List<Report> getReportsByReporter(Long reporterId);

    List<Report> getReportsByReportedUser(Long reportedUserId);

    int countReportsByReportedUser(Long reportedUserId);

    int countReportsByReporter(Long reporterId);

    /**
     * Finaliza un reporte como RESOLVED tras una acción de moderación distinta de cerrar. Método interno
     * (no expuesto como endpoint): lo invocan los métodos de ModerationService. Encapsula la transición de
     * estado + el registro de la {@link ReportActionType} indicada + la notificación al reporter sobre cómo
     * se resolvió. Idempotente si ya está RESOLVED; falla si el reporte estaba CLOSED.
     */
    void resolveReport(Long reportId, Long adminId, ReportActionType actionType);

    /**
     * Finaliza un reporte como CLOSED (cerrado SIN tomar ninguna acción de moderación). Método interno
     * (no expuesto como endpoint): lo invoca ModerationService.closeReport. Encapsula la transición de
     * estado + el registro de la acción CLOSE + la notificación al reporter. Idempotente si ya está CLOSED.
     */
    void closeReport(Long reportId, Long adminId);
}
