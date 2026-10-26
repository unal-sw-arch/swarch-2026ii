package com.parosurvivors.serviya.feedback.infrastructure.mappers;

import com.parosurvivors.serviya.feedback.application.dto.command.SubmitClientFeedbackCommand;
import com.parosurvivors.serviya.feedback.application.dto.result.ClientFeedbackResult;
import com.parosurvivors.serviya.feedback.domain.ClientFeedbackTagCatalog;
import com.parosurvivors.serviya.feedback.infrastructure.dto.form.SubmitClientFeedbackForm;
import com.parosurvivors.serviya.feedback.infrastructure.dto.response.ClientFeedbackResponse;
import com.parosurvivors.serviya.feedback.infrastructure.dto.response.ClientFeedbackTagResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper web (MapStruct) del feedback de cliente: Form->Command y Result/dominio->Response.
 * TODO: completar mapeos donde difieran los nombres de campo.
 */
@Mapper(componentModel = "spring")
public interface ClientFeedbackWebMapper {

    @Mapping(target = "offererId", source = "offererId")
    @Mapping(target = "requestId", source = "requestId")
    SubmitClientFeedbackCommand toCommand(SubmitClientFeedbackForm form, Long offererId, Long requestId);

    ClientFeedbackResponse toResponse(ClientFeedbackResult result);

    ClientFeedbackTagResponse toResponse(ClientFeedbackTagCatalog tag);

    List<ClientFeedbackTagResponse> toTagResponses(List<ClientFeedbackTagCatalog> tags);
}
