package com.parosurvivors.serviya.bff.infrastructure.mappers;

import com.parosurvivors.serviya.bff.application.dto.ClientMetricsView;
import com.parosurvivors.serviya.bff.application.dto.ClientTagMetricView;
import com.parosurvivors.serviya.bff.application.dto.OffererMetricsView;
import com.parosurvivors.serviya.bff.application.dto.OffererTagMetricView;
import com.parosurvivors.serviya.bff.application.dto.OffererPublicProfileView;
import com.parosurvivors.serviya.bff.application.dto.OffererSummaryView;
import com.parosurvivors.serviya.bff.application.dto.ServiceDetailView;
import com.parosurvivors.serviya.bff.application.dto.ServiceSummaryView;
import com.parosurvivors.serviya.bff.application.dto.UserMetricsView;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.ClientMetricsResponse;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.ClientTagMetricsResponse;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.OffererMetricsResponse;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.OffererProfileSummaryResponse;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.OffererPublicProfileDetailResponse;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.OffererTagMetricsResponse;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.ServiceDetailResponse;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.ServiceResponse;
import com.parosurvivors.serviya.bff.infrastructure.dto.response.UserMetricsResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BffWebMapper {

    OffererPublicProfileDetailResponse toResponse(OffererPublicProfileView view);

    OffererProfileSummaryResponse toResponse(OffererSummaryView view);

    ServiceResponse toResponse(ServiceSummaryView view);

    ServiceDetailResponse toResponse(ServiceDetailView view);

    OffererTagMetricsResponse toResponse(OffererTagMetricView view);

    ClientTagMetricsResponse toResponse(ClientTagMetricView view);

    OffererMetricsResponse toResponse(OffererMetricsView view);

    ClientMetricsResponse toResponse(ClientMetricsView view);

    UserMetricsResponse toResponse(UserMetricsView view);
}
