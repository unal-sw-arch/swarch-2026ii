package com.parosurvivors.serviya.reports.infrastructure.mappers;

import com.parosurvivors.serviya.reports.domain.ClientFeedbackReport;
import com.parosurvivors.serviya.reports.infrastructure.entities.ClientFeedbackReportEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClientFeedbackReportPersistenceMapper {

    ClientFeedbackReport toDomain(ClientFeedbackReportEntity entity);

    ClientFeedbackReportEntity toEntity(ClientFeedbackReport domain);
}
