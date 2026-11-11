package com.parosurvivors.serviya.reports.api;

import com.parosurvivors.serviya.reports.api.dto.ReportActionSnapshot;
import com.parosurvivors.serviya.reports.api.dto.ReportSnapshot;

/**
 * Comandos publicos del modulo reports.
 */
public interface ReportCommandApi {

    ReportSnapshot createBaseReport(
            Long reporterId, Long reportedUserId, String type, String category, String reason);

    void resolveReport(Long reportId, Long adminId, String actionType);

    void closeReport(Long reportId, Long adminId);

    ReportActionSnapshot createAction(Long reportId, Long adminId, String actionType);

    /** Crea el reporte base + el enlace al feedback de servicio reportado. */
    Long createServiceFeedbackReport(
            Long reporterId, Long reportedUserId, String category, String reason, Long feedbackId);

    /** Crea el reporte base + el enlace al feedback de cliente reportado. */
    Long createClientFeedbackReport(
            Long reporterId, Long reportedUserId, String category, String reason, Long feedbackId);
}
