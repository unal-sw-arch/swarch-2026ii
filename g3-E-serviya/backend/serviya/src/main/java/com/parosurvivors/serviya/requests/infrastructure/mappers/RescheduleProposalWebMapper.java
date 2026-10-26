package com.parosurvivors.serviya.requests.infrastructure.mappers;

import com.parosurvivors.serviya.requests.application.dto.command.CreateRescheduleProposalCommand;
import com.parosurvivors.serviya.requests.application.dto.item.RescheduleProposalItem;
import com.parosurvivors.serviya.requests.application.dto.query.SearchRescheduleProposalsQuery;
import com.parosurvivors.serviya.requests.application.dto.result.RescheduleProposalDetailResult;
import com.parosurvivors.serviya.requests.domain.RescheduleProposal;
import com.parosurvivors.serviya.requests.infrastructure.dto.form.CreateRescheduleProposalForm;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.RescheduleProposalDetailResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.RescheduleProposalResponse;
import com.parosurvivors.serviya.requests.infrastructure.dto.response.RescheduleProposalSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mapper web (MapStruct) de propuestas de reprogramacion: Form->Command, params->Query y
 * dominio/Item/Result->Response.
 */
@Mapper(componentModel = "spring")
public interface RescheduleProposalWebMapper {

    @Mapping(target = "offererId", source = "offererId")
    CreateRescheduleProposalCommand toCommand(CreateRescheduleProposalForm form, Long offererId);

    /** Arma el Query de busqueda desde el viewer (JWT) y los query params (cada uno mapea por nombre). */
    SearchRescheduleProposalsQuery toQuery(Long viewerId, List<String> statuses,
                                           LocalDateTime proposedFrom, LocalDateTime proposedTo,
                                           LocalDateTime createdFrom, LocalDateTime createdTo,
                                           Long serviceId);

    RescheduleProposalSummaryResponse toSummaryResponse(RescheduleProposalItem item);

    RescheduleProposalDetailResponse toDetailResponse(RescheduleProposalDetailResult result);

    RescheduleProposalResponse toResponse(RescheduleProposal proposal);

    List<RescheduleProposalResponse> toResponses(List<RescheduleProposal> proposals);
}
