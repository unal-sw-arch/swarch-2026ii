package com.parosurvivors.serviya.feedback.infrastructure.mappers;

import com.parosurvivors.serviya.feedback.domain.ClientFeedbackTagCatalog;
import com.parosurvivors.serviya.feedback.infrastructure.entities.ClientFeedbackTagCatalogEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClientFeedbackTagCatalogPersistenceMapper {

    ClientFeedbackTagCatalog toDomain(ClientFeedbackTagCatalogEntity entity);

    ClientFeedbackTagCatalogEntity toEntity(ClientFeedbackTagCatalog domain);
}
