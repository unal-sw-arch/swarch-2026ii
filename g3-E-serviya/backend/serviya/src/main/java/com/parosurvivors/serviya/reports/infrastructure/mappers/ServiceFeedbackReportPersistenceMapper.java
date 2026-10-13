package com.parosurvivors.serviya.reports.infrastructure.mappers;

import com.parosurvivors.serviya.reports.domain.ServiceFeedbackReport;
import com.parosurvivors.serviya.reports.infrastructure.entities.ServiceFeedbackReportEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ServiceFeedbackReportPersistenceMapper {

    ServiceFeedbackReport toDomain(ServiceFeedbackReportEntity entity);

    ServiceFeedbackReportEntity toEntity(ServiceFeedbackReport domain);
}
