package com.parosurvivors.serviya.reports.infrastructure.mappers;

import com.parosurvivors.serviya.reports.application.dto.command.CreateClientFeedbackReportCommand;
import com.parosurvivors.serviya.reports.application.dto.command.CreateRequestReportCommand;
import com.parosurvivors.serviya.reports.application.dto.command.CreateServiceFeedbackReportCommand;
import com.parosurvivors.serviya.reports.application.dto.result.ReportDetailResult;
import com.parosurvivors.serviya.reports.domain.ClientFeedbackReport;
import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.reports.domain.ReportAction;
import com.parosurvivors.serviya.reports.domain.RequestReport;
import com.parosurvivors.serviya.reports.domain.ServiceFeedbackReport;
import com.parosurvivors.serviya.reports.infrastructure.dto.form.CreateClientFeedbackReportForm;
import com.parosurvivors.serviya.reports.infrastructure.dto.form.CreateRequestReportForm;
import com.parosurvivors.serviya.reports.infrastructure.dto.form.CreateServiceFeedbackReportForm;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.ClientFeedbackReportResponse;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.ReportActionResponse;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.ReportDetailResponse;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.ReportResponse;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.RequestReportResponse;
import com.parosurvivors.serviya.reports.infrastructure.dto.response.ServiceFeedbackReportResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper web (MapStruct) de reportes: Form->Command y dominio/Result->Response.
 * TODO: completar mapeos donde difieran los nombres de campo.
 */
@Mapper(componentModel = "spring")
public interface ReportWebMapper {

    @Mapping(target = "reporterId", source = "reporterId")
    CreateRequestReportCommand toCommand(CreateRequestReportForm form, Long reporterId);

    @Mapping(target = "reporterId", source = "reporterId")
    CreateServiceFeedbackReportCommand toCommand(CreateServiceFeedbackReportForm form, Long reporterId);

    @Mapping(target = "reporterId", source = "reporterId")
    CreateClientFeedbackReportCommand toCommand(CreateClientFeedbackReportForm form, Long reporterId);

    ReportResponse toResponse(Report report);

    ReportDetailResponse toResponse(ReportDetailResult result);

    ReportActionResponse toResponse(ReportAction action);

    RequestReportResponse toResponse(RequestReport report);

    ServiceFeedbackReportResponse toResponse(ServiceFeedbackReport report);

    ClientFeedbackReportResponse toResponse(ClientFeedbackReport report);

    List<ReportResponse> toReportResponses(List<Report> reports);

    List<ReportActionResponse> toActionResponses(List<ReportAction> actions);
}
