package com.parosurvivors.serviya.feedback.infrastructure.mappers;

import com.parosurvivors.serviya.feedback.domain.ClientFeedback;
import com.parosurvivors.serviya.feedback.infrastructure.entities.ClientFeedbackEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@code tagIds} se persiste en la tabla puente client_feedback_tags y se gestiona aparte
 * en el adaptador; por eso se ignora en este mapper.
 */
@Mapper(componentModel = "spring")
public interface ClientFeedbackPersistenceMapper {

    @Mapping(target = "tagIds", ignore = true)
    ClientFeedback toDomain(ClientFeedbackEntity entity);

    ClientFeedbackEntity toEntity(ClientFeedback domain);
}
