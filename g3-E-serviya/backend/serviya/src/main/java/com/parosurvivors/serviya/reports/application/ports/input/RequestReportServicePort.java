package com.parosurvivors.serviya.reports.application.ports.input;

import com.parosurvivors.serviya.reports.application.dto.command.CreateRequestReportCommand;
import com.parosurvivors.serviya.reports.application.dto.result.ReportDetailResult;
import com.parosurvivors.serviya.reports.domain.RequestReport;

/**
 * Puerto de entrada de RequestReportService (subtipo REQUEST). createReport recibe Command y devuelve
 * el dominio del enlace; el detalle devuelve el Result paraguas. Nunca tipos web.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 7).
 */
public interface RequestReportServicePort {

    RequestReport createReport(CreateRequestReportCommand command);

    ReportDetailResult getReportDetail(Long reportId);
}
