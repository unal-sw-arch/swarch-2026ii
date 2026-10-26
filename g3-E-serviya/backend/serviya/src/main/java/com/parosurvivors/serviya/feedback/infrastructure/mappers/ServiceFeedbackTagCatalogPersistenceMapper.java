package com.parosurvivors.serviya.feedback.infrastructure.mappers;

import com.parosurvivors.serviya.feedback.domain.ServiceFeedbackTagCatalog;
import com.parosurvivors.serviya.feedback.infrastructure.entities.ServiceFeedbackTagCatalogEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ServiceFeedbackTagCatalogPersistenceMapper {

    ServiceFeedbackTagCatalog toDomain(ServiceFeedbackTagCatalogEntity entity);

    ServiceFeedbackTagCatalogEntity toEntity(ServiceFeedbackTagCatalog domain);
}
