package com.parosurvivors.serviya.reports.application.ports.input;

import com.parosurvivors.serviya.reports.application.dto.command.CreateClientFeedbackReportCommand;
import com.parosurvivors.serviya.reports.application.dto.result.ReportDetailResult;
import com.parosurvivors.serviya.reports.domain.ClientFeedbackReport;

/**
 * Puerto de entrada de ClientFeedbackReportService (subtipo CLIENT_FEEDBACK). createReport recibe Command y
 * devuelve el dominio del enlace; el detalle devuelve el Result paraguas. Nunca tipos web.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 7).
 */
public interface ClientFeedbackReportServicePort {

    ClientFeedbackReport createReport(CreateClientFeedbackReportCommand command);

    ReportDetailResult getReportDetail(Long reportId);
}
