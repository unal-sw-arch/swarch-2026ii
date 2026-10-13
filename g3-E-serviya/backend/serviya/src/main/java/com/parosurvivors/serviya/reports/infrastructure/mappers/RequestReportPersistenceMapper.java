package com.parosurvivors.serviya.reports.infrastructure.mappers;

import com.parosurvivors.serviya.reports.domain.RequestReport;
import com.parosurvivors.serviya.reports.infrastructure.entities.RequestReportEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RequestReportPersistenceMapper {

    RequestReport toDomain(RequestReportEntity entity);

    RequestReportEntity toEntity(RequestReport domain);
}
