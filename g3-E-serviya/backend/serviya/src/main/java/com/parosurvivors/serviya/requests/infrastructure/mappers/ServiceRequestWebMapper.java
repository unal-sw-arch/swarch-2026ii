package com.parosurvivors.serviya.requests.infrastructure.mappers;

import com.parosurvivors.serviya.requests.application.dto.command.CreateServiceRequestCommand;
import com.parosurvivors.serviya.requests.application.dto.item.RequestHistoryItem;
import com.parosurvivors.serviya.requests.application.dto.item.ServiceRequestSummaryItem;
import com.parosurvivors.serviya.requests.application.dto.query.SearchServiceRequestsQuery;
import com.parosurvivors.serviya.requests.application.dto.result.AdminRequestDetailResult;
import com.parosurvivors.serviya.requests.application.dto.result.ServiceRequestDetailResult;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;
import com.parosurvivors.serviya.requests.infrastructure.dto.form.CreateServiceRequestForm;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.AdminRequestDetailResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.RequestHistoryResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.ServiceRequestDetailResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.ServiceRequestResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.ServiceRequestSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapper web (MapStruct) de solicitudes de servicio: Form->Command, params->Query y
 * dominio/Result/Item->Response.
 */
@Mapper(componentModel = "spring")
public interface ServiceRequestWebMapper {

    @Mapping(target = "clientId", source = "clientId")
    CreateServiceRequestCommand toCommand(CreateServiceRequestForm form, Long clientId);

    /** Arma el Query de listado desde el viewer (JWT) y los query params (cada uno mapea por nombre). */
    SearchServiceRequestsQuery toQuery(Long viewerId, List<String> statuses, Long serviceId,
                                       Long categoryId, Long counterpartyId, String titleQuery,
                                       LocalDateTime scheduledFrom, LocalDateTime scheduledTo,
                                       LocalDateTime createdFrom, LocalDateTime createdTo);

    ServiceRequestSummaryResponse toResponse(ServiceRequestSummaryItem item);

    ServiceRequestResponse toResponse(ServiceRequest request);

    ServiceRequestDetailResponse toResponse(ServiceRequestDetailResult result);

    AdminRequestDetailResponse toResponse(AdminRequestDetailResult result);

    RequestHistoryResponse toResponse(RequestHistoryItem item);

    List<RequestHistoryResponse> toHistoryResponses(List<RequestHistoryItem> items);
}
