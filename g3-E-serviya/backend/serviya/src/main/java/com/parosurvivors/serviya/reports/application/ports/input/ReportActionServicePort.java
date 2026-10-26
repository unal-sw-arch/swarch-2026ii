package com.parosurvivors.serviya.reports.application.ports.input;

import com.parosurvivors.serviya.reports.domain.ReportAction;
import com.parosurvivors.serviya.reports.domain.ReportActionType;

import java.util.List;

/**
 * Puerto de entrada de ReportActionService — trazabilidad de acciones de admin sobre reportes.
 * Las acciones son registros automáticos: el llamador (ModerationService) indica el
 * {@link ReportActionType} y el servicio genera+persiste la descripción preestablecida.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 7).
 */
public interface ReportActionServicePort {

    ReportAction createAction(Long reportId, Long adminId, ReportActionType actionType);

    List<ReportAction> getActionsByReport(Long reportId);
}
