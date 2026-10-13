package com.parosurvivors.serviya.services.infrastructure.mappers;

import com.parosurvivors.serviya.metrics.domain.ServiceMetrics;
import com.parosurvivors.serviya.services.application.dto.command.CreateServiceCommand;
import com.parosurvivors.serviya.services.application.dto.command.UpdateServiceCommand;
import com.parosurvivors.serviya.services.domain.FeedbackUser;
import com.parosurvivors.serviya.services.domain.Service;
import com.parosurvivors.serviya.services.domain.ServiceDetail;
import com.parosurvivors.serviya.services.infrastructure.dto.form.CreateServiceForm;
import com.parosurvivors.serviya.services.infrastructure.dto.form.UpdateServiceForm;
import com.parosurvivors.serviya.services.infrastructure.dto.response.FeedbackResponse;
import com.parosurvivors.serviya.services.infrastructure.dto.response.ServiceDetailResponse;
import com.parosurvivors.serviya.services.infrastructure.dto.response.ServiceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {CategoryWebMapper.class, ServiceAvailabilityWebMapper.class})
public interface ServiceWebMapper {

    @Mapping(target = "offererId", source = "offererId")
    CreateServiceCommand toCommand(CreateServiceForm form, Long offererId);

    @Mapping(target = "serviceId", source = "serviceId")
    UpdateServiceCommand toCommand(UpdateServiceForm form, Long serviceId);

    @Mapping(target = "offererName", ignore = true)
    ServiceResponse toResponse(Service service);

    @Mapping(target = "id", source = "service.id")
    @Mapping(target = "offererId", source = "service.offererId")
    @Mapping(target = "offererName", ignore = true)
    @Mapping(target = "title", source = "service.title")
    @Mapping(target = "description", source = "service.description")
    @Mapping(target = "photos", source = "service.photos")
    @Mapping(target = "priceHourly", source = "service.priceHourly")
    @Mapping(target = "categoryId", source = "service.categoryId")
    @Mapping(target = "averageDurationMinutes", source = "service.averageDurationMinutes")
    @Mapping(target = "active", source = "service.active")
    @Mapping(target = "operationRadiusKm", source = "service.operationRadiusKm")
    @Mapping(target = "createdAt", source = "service.createdAt")
    @Mapping(target = "updatedAt", source = "service.updatedAt")
    @Mapping(target = "deletedAt", source = "service.deletedAt")
    @Mapping(target = "averageRating", source = "metrics.averageRating")
    @Mapping(target = "totalRatings", source = "metrics.totalRatings")
    @Mapping(target = "totalComments", source = "metrics.totalComments")
    ServiceResponse toResponse(Service service, ServiceMetrics metrics);

    @Mapping(target = "id", source = "feedback.id")
    @Mapping(target = "requestId", source = "feedback.requestId")
    @Mapping(target = "userId", source = "feedback.clientId")
    @Mapping(target = "comment", source = "feedback.comment")
    @Mapping(target = "rating", source = "feedback.rating")
    @Mapping(target = "createdAt", source = "feedback.createdAt")
    @Mapping(target = "userName", source = "user.fullName")
    @Mapping(target = "userPhotoUrl", source = "user.profilePhotoUrl")
    FeedbackResponse toFeedbackResponse(FeedbackUser feedbackUser);


    @Mapping(target = "id", source = "service.id")
    @Mapping(target = "userId", source = "service.offererId")
    @Mapping(target = "fullName", source = "offererSummary.fullName")
    @Mapping(target = "profilePhotoUrl", source = "offererSummary.profilePhotoUrl")
    @Mapping(target = "specialty", source = "offererSummary.specialty")
    @Mapping(target = "whatsappNumber", source = "offererProfile.whatsappNumber")
    @Mapping(target = "publicDescription", source = "offererProfile.publicDescription")
    @Mapping(target = "offererAverageRating", source = "offererSummary.averageRating")
    @Mapping(target = "feedbacks", source = "feedbackUsers")
    @Mapping(target = "title", source = "service.title")
    @Mapping(target = "description", source = "service.description")
    @Mapping(target = "photos", source = "service.photos")
    @Mapping(target = "priceHourly", source = "service.priceHourly")
    @Mapping(target = "averageDurationMinutes", source = "service.averageDurationMinutes")
    @Mapping(target = "active", source = "service.active")
    @Mapping(target = "operationRadiusKm", source = "service.operationRadiusKm")
    @Mapping(target = "createdAt", source = "service.createdAt")
    @Mapping(target = "updatedAt", source = "service.updatedAt")
    @Mapping(target = "deletedAt", source = "service.deletedAt")
    @Mapping(target = "serviceAverageRating", source = "serviceMetrics.averageRating")
    @Mapping(target = "serviceTotalRatings", source = "serviceMetrics.totalRatings")
    @Mapping(target = "serviceTotalComments", source = "serviceMetrics.totalComments")
    @Mapping(target = "totalCompletedServices", source = "offererMetrics.totalCompletedServices")
    @Mapping(target = "totalCancelledServices", source = "offererMetrics.totalCancelledServices")
    ServiceDetailResponse toDetailResponse(ServiceDetail detail);

    List<ServiceResponse> toResponses(List<Service> services);
}
