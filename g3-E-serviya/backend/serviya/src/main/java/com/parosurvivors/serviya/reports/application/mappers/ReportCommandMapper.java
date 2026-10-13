package com.parosurvivors.serviya.reports.application.mappers;

import com.parosurvivors.serviya.reports.application.dto.command.CreateClientFeedbackReportCommand;
import com.parosurvivors.serviya.reports.application.dto.command.CreateRequestReportCommand;
import com.parosurvivors.serviya.reports.application.dto.command.CreateServiceFeedbackReportCommand;
import com.parosurvivors.serviya.reports.domain.ClientFeedbackReport;
import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.reports.domain.RequestReport;
import com.parosurvivors.serviya.reports.domain.ServiceFeedbackReport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper de aplicacion (MapStruct) Command -> dominio de reportes. Capa de aplicacion.
 * PLACEHOLDER: cada create genera el Report base + el enlace del subtipo. El reportType, estado/prioridad
 * y timestamps los fija el servicio; el reportId del enlace se completa tras persistir el Report base.
 */
@Mapper(componentModel = "spring")
public interface ReportCommandMapper {

    // ---- Report base (campos comunes) por subtipo ----
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reportType", ignore = true) // TODO REQUEST
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "priority", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Report toBaseReport(CreateRequestReportCommand command);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reportType", ignore = true) // TODO SERVICE_FEEDBACK
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "priority", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Report toBaseReport(CreateServiceFeedbackReportCommand command);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reportType", ignore = true) // TODO CLIENT_FEEDBACK
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "priority", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Report toBaseReport(CreateClientFeedbackReportCommand command);

    // ---- Enlaces de subtipo ----
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reportId", ignore = true) // se fija tras persistir el Report base
    RequestReport toRequestReport(CreateRequestReportCommand command);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reportId", ignore = true)
    @Mapping(target = "feedbackId", source = "serviceFeedbackId")
    ServiceFeedbackReport toServiceFeedbackReport(CreateServiceFeedbackReportCommand command);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reportId", ignore = true)
    @Mapping(target = "feedbackId", source = "clientFeedbackId")
    ClientFeedbackReport toClientFeedbackReport(CreateClientFeedbackReportCommand command);
}
