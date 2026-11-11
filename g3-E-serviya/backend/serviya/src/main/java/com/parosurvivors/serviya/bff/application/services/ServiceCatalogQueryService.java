package com.parosurvivors.serviya.bff.application.services;

import com.parosurvivors.serviya.bff.application.dto.ServiceDetailView;
import com.parosurvivors.serviya.bff.application.dto.ServiceDetailView.AvailabilityView;
import com.parosurvivors.serviya.bff.application.dto.ServiceDetailView.CategoryView;
import com.parosurvivors.serviya.bff.application.dto.ServiceDetailView.FeedbackView;
import com.parosurvivors.serviya.bff.application.dto.ServiceSearchRequest;
import com.parosurvivors.serviya.bff.application.dto.ServiceSummaryView;
import com.parosurvivors.serviya.feedback.api.FeedbackQueryApi;
import com.parosurvivors.serviya.feedback.api.dto.ServiceFeedbackSnapshot;
import com.parosurvivors.serviya.metrics.api.MetricsQueryApi;
import com.parosurvivors.serviya.metrics.api.dto.OffererMetricsSnapshot;
import com.parosurvivors.serviya.metrics.api.dto.ServiceMetricsSnapshot;
import com.parosurvivors.serviya.profiles.api.ProfileQueryApi;
import com.parosurvivors.serviya.profiles.api.dto.OffererProfileSnapshot;
import com.parosurvivors.serviya.profiles.api.dto.OffererProfileSummarySnapshot;
import com.parosurvivors.serviya.profiles.api.dto.ProfileSnapshot;
import com.parosurvivors.serviya.services.api.ServiceQueryApi;
import com.parosurvivors.serviya.services.api.dto.CategorySnapshot;
import com.parosurvivors.serviya.services.api.dto.ServiceSearchCriteria;
import com.parosurvivors.serviya.services.api.dto.ServiceSnapshot;
import com.parosurvivors.serviya.shared.contract.PageResult;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Composicion de las lecturas del catalogo de servicios. Resuelve filtros cross-module con
 * profiles.api/metrics.api y enriquece las respuestas con feedback/profiles/metrics.
 */
@Component
@RequiredArgsConstructor
public class ServiceCatalogQueryService {

    private static final int RECENT_FEEDBACK_LIMIT = 3;

    private final ServiceQueryApi serviceQueryApi;
    private final ProfileQueryApi profileQueryApi;
    private final MetricsQueryApi metricsQueryApi;
    private final FeedbackQueryApi feedbackQueryApi;

    public Page<ServiceSummaryView> search(ServiceSearchRequest request, Pageable pageable) {
        ServiceSearchCriteria criteria = new ServiceSearchCriteria(
                request.name(), request.categoryId(), request.offererId(),
                request.minPrice(), request.maxPrice(), request.available(),
                request.minRating(), request.maxRating(),
                request.latitude(), request.longitude(), request.maxDistanceKm(),
                null, null, null);

        if (request.minRating() != null || request.maxRating() != null) {
            List<Long> serviceIds = metricsQueryApi.findServiceIdsByAverageRating(
                    request.minRating(), request.maxRating());
            if (serviceIds.isEmpty()) {
                return Page.empty(pageable);
            }
            criteria = criteria.withServiceIds(serviceIds);
        }

        if (request.name() != null && !request.name().isBlank()) {
            criteria = criteria.withOffererIdsByName(
                    profileQueryApi.findUserIdsByFullName(request.name()));
        }

        if (request.latitude() != null && request.longitude() != null
                && request.maxDistanceKm() != null) {
            List<Long> offererIds = profileQueryApi.findUserIdsWithinDistance(
                    request.latitude(), request.longitude(), request.maxDistanceKm());
            if (offererIds.isEmpty()) {
                return Page.empty(pageable);
            }
            criteria = criteria.withOffererIdsWithinDistance(offererIds);
        }

        PageResult<ServiceSnapshot> result = serviceQueryApi.search(
                criteria, pageable.getPageNumber(), pageable.getPageSize());
        List<ServiceSnapshot> services = result.content();
        if (services.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, result.totalElements());
        }

        Map<Long, ServiceMetricsSnapshot> metrics = metricsQueryApi.getServiceMetrics(
                services.stream().map(ServiceSnapshot::id).toList());
        Map<Long, ProfileSnapshot> offerers = profilesById(
                services.stream().map(ServiceSnapshot::offererId).toList());

        List<ServiceSummaryView> content = services.stream()
                .map(service -> toSummary(service, metrics.get(service.id()), offerers))
                .toList();
        return new PageImpl<>(content, pageable, result.totalElements());
    }

    public ServiceDetailView getDetail(Long serviceId) {
        ServiceSnapshot service = serviceQueryApi.findById(serviceId)
                .filter(snapshot -> snapshot.deletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Servicio no encontrado con id: " + serviceId));

        CategorySnapshot category = serviceQueryApi.findCategory(service.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Categoria no encontrada con id: " + service.categoryId()));
        OffererProfileSnapshot offererProfile = profileQueryApi.findOffererProfile(service.offererId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Perfil de oferente no encontrado para userId: " + service.offererId()));
        OffererProfileSummarySnapshot summary = profileQueryApi.getOffererSummary(service.offererId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Resumen de oferente no encontrado para userId: " + service.offererId()));
        OffererMetricsSnapshot offererMetrics = metricsQueryApi.getOffererMainMetrics(service.offererId());
        ServiceMetricsSnapshot serviceMetrics = metricsQueryApi.getServiceMetrics(service.id());

        List<ServiceFeedbackSnapshot> recentFeedback = feedbackQueryApi.getRecentServiceFeedback(
                service.id(), RECENT_FEEDBACK_LIMIT);
        Map<Long, ProfileSnapshot> feedbackProfiles = profilesById(
                recentFeedback.stream().map(ServiceFeedbackSnapshot::clientId).toList());

        List<AvailabilityView> availability = serviceQueryApi.getAvailabilities(service.id()).stream()
                .filter(slot -> slot.active())
                .map(slot -> new AvailabilityView(
                        slot.id(), (byte) slot.weekDay(), slot.startTime(), slot.endTime(), slot.active()))
                .toList();

        return new ServiceDetailView(
                service.id(),
                service.offererId(),
                summary.fullName(),
                summary.profilePhotoUrl(),
                summary.specialty(),
                offererProfile.whatsappNumber(),
                offererProfile.publicDescription(),
                offererMetrics.averageRating(),
                new CategoryView(category.id(), category.name()),
                recentFeedback.stream()
                        .map(feedback -> toFeedback(feedback, feedbackProfiles.get(feedback.clientId())))
                        .toList(),
                availability,
                service.title(),
                service.description(),
                service.photos(),
                service.priceHourly(),
                service.averageDurationMinutes(),
                service.active(),
                service.operationRadiusKm(),
                service.createdAt(),
                service.updatedAt(),
                service.deletedAt(),
                serviceMetrics.averageRating(),
                serviceMetrics.totalRatings(),
                serviceMetrics.totalComments(),
                offererMetrics.totalCompletedServices(),
                offererMetrics.totalCancelledServices());
    }

    /**
     * Servicios de un oferente con su categoria. Mismo comportamiento historico: 404 si no tiene
     * servicios y 404 si alguna categoria no existe.
     */
    public List<ServiceDetailView> getByOfferer(Long offererId) {
        List<ServiceSnapshot> services = serviceQueryApi.findByOffererId(offererId);
        if (services.isEmpty()) {
            throw new ResourceNotFoundException("El oferente no tiene servicios");
        }
        Map<Long, String> categoryNames = serviceQueryApi.getCategories().stream()
                .collect(Collectors.toMap(CategorySnapshot::id, CategorySnapshot::name));

        return services.stream()
                .map(service -> {
                    String categoryName = categoryNames.get(service.categoryId());
                    if (categoryName == null) {
                        throw new ResourceNotFoundException(
                                "Categoria no encontrada con id: " + service.categoryId());
                    }
                    CategoryView category = new CategoryView(service.categoryId(), categoryName);
                    return new ServiceDetailView(
                            service.id(), service.offererId(), null, null, null, null, null, null,
                            category, List.of(), List.of(),
                            service.title(), service.description(), service.photos(),
                            service.priceHourly(), service.averageDurationMinutes(), service.active(),
                            service.operationRadiusKm(), service.createdAt(), service.updatedAt(),
                            service.deletedAt(), null, null, null, null, null);
                })
                .toList();
    }

    private ServiceSummaryView toSummary(
            ServiceSnapshot service,
            ServiceMetricsSnapshot metrics,
            Map<Long, ProfileSnapshot> offerers) {
        ProfileSnapshot offerer = offerers.get(service.offererId());
        return new ServiceSummaryView(
                service.id(),
                service.offererId(),
                offerer == null ? "Oferente" : offerer.fullName(),
                service.title(),
                service.description(),
                service.photos(),
                service.priceHourly(),
                service.categoryId(),
                service.averageDurationMinutes(),
                service.active(),
                service.operationRadiusKm(),
                service.createdAt(),
                service.updatedAt(),
                service.deletedAt(),
                metrics == null ? null : metrics.averageRating(),
                metrics == null ? null : metrics.totalRatings(),
                metrics == null ? null : metrics.totalComments());
    }

    private FeedbackView toFeedback(ServiceFeedbackSnapshot feedback, ProfileSnapshot user) {
        return new FeedbackView(
                feedback.id(),
                feedback.requestId(),
                feedback.clientId(),
                user == null ? null : user.fullName(),
                user == null ? null : user.profilePhotoUrl(),
                feedback.comment(),
                feedback.rating(),
                feedback.createdAt());
    }

    private Map<Long, ProfileSnapshot> profilesById(Collection<Long> userIds) {
        List<Long> distinct = userIds.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return profileQueryApi.findProfiles(distinct).stream()
                .collect(Collectors.toMap(ProfileSnapshot::userId, Function.identity()));
    }
}
