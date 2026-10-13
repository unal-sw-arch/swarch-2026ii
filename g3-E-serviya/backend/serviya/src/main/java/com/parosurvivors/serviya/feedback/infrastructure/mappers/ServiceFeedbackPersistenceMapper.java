package com.parosurvivors.serviya.feedback.infrastructure.mappers;

import com.parosurvivors.serviya.feedback.domain.ServiceFeedback;
import com.parosurvivors.serviya.feedback.infrastructure.entities.ServiceFeedbackEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@code tagIds} se persiste en la tabla puente service_feedback_tags y se gestiona aparte
 * en el adaptador; por eso se ignora en este mapper.
 */
@Mapper(componentModel = "spring")
public interface ServiceFeedbackPersistenceMapper {

    @Mapping(target = "tagIds", ignore = true)
    ServiceFeedback toDomain(ServiceFeedbackEntity entity);

    ServiceFeedbackEntity toEntity(ServiceFeedback domain);
}
