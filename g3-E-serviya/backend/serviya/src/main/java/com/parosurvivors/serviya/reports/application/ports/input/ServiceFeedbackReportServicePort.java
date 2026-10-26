package com.parosurvivors.serviya.reports.application.ports.input;

import com.parosurvivors.serviya.reports.application.dto.command.CreateServiceFeedbackReportCommand;
import com.parosurvivors.serviya.reports.application.dto.result.ReportDetailResult;
import com.parosurvivors.serviya.reports.domain.ServiceFeedbackReport;

/**
 * Puerto de entrada de ServiceFeedbackReportService (subtipo SERVICE_FEEDBACK). createReport recibe Command y
 * devuelve el dominio del enlace; el detalle devuelve el Result paraguas. Nunca tipos web.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 7).
 */
public interface ServiceFeedbackReportServicePort {

    ServiceFeedbackReport createReport(CreateServiceFeedbackReportCommand command);

    ReportDetailResult getReportDetail(Long reportId);
}
