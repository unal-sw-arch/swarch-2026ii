package com.parosurvivors.serviya.requests.infrastructure.mappers;

import com.parosurvivors.serviya.requests.domain.ServiceRequest;
import com.parosurvivors.serviya.requests.infrastructure.entities.ServiceRequestEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ServiceRequestPersistenceMapper {

    ServiceRequest toDomain(ServiceRequestEntity entity);

    ServiceRequestEntity toEntity(ServiceRequest domain);
}
