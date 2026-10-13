package com.parosurvivors.serviya.reports.infrastructure.mappers;

import com.parosurvivors.serviya.reports.domain.Report;
import com.parosurvivors.serviya.reports.infrastructure.entities.ReportEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReportPersistenceMapper {

    Report toDomain(ReportEntity entity);

    ReportEntity toEntity(Report domain);
}
