package com.parosurvivors.serviya.reports.infrastructure.mappers;

import com.parosurvivors.serviya.reports.domain.ReportAction;
import com.parosurvivors.serviya.reports.infrastructure.entities.ReportActionEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReportActionPersistenceMapper {

    ReportAction toDomain(ReportActionEntity entity);

    ReportActionEntity toEntity(ReportAction domain);
}
