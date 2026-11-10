package com.parosurvivors.serviya.bff.application.services;

import com.parosurvivors.serviya.bff.application.dto.OffererPublicProfileView;
import com.parosurvivors.serviya.bff.application.dto.OffererPublicProfileView.PublishedServiceView;
import com.parosurvivors.serviya.bff.application.dto.OffererSummaryView;
import com.parosurvivors.serviya.metrics.api.MetricsQueryApi;
import com.parosurvivors.serviya.metrics.api.dto.OffererMetricsSnapshot;
import com.parosurvivors.serviya.profiles.api.ProfileQueryApi;
import com.parosurvivors.serviya.profiles.api.dto.OffererProfileSnapshot;
import com.parosurvivors.serviya.profiles.api.dto.ProfileSnapshot;
import com.parosurvivors.serviya.services.api.ServiceQueryApi;
import com.parosurvivors.serviya.services.api.dto.CategorySnapshot;
import com.parosurvivors.serviya.services.api.dto.ServiceSnapshot;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Composicion de las lecturas publicas del oferente (RF-027). Vive en el BFF para que profiles no
 * dependa de services ni de metrics: esos modulos quedan como proveedores de datos propios.
 */
@Component
@RequiredArgsConstructor
public class PublicOffererQueryService {

    private final ProfileQueryApi profileQueryApi;
    private final MetricsQueryApi metricsQueryApi;
    private final ServiceQueryApi serviceQueryApi;

    public OffererPublicProfileView getPublicProfile(Long userId) {
        // Mismo orden de comprobaciones que el servicio anterior: oferente antes que perfil personal.
        OffererProfileSnapshot offererProfile = profileQueryApi.findOffererProfile(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Offerer profile not found for userId: " + userId));
        ProfileSnapshot profile = profileQueryApi.findProfile(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User profile not found for userId: " + userId));

        OffererMetricsSnapshot metrics = metricsQueryApi.getOffererMainMetrics(userId);
        List<PublishedServiceView> services = activeServicesOf(userId);

        return new OffererPublicProfileView(
                userId,
                profile.fullName(),
                profile.profilePhotoUrl(),
                offererProfile.specialty(),
                offererProfile.publicDescription(),
                offererProfile.whatsappNumber(),
                metrics.averageRating(),
                metrics.totalRatings(),
                metrics.totalComments(),
                metrics.totalPositiveTags(),
                metrics.totalNegativeTags(),
                metrics.totalCompletedServices(),
                metrics.totalCancelledServices(),
                metrics.totalNotProvidedServices(),
                services);
    }

    public OffererSummaryView getSummary(Long userId) {
        OffererProfileSnapshot offererProfile = profileQueryApi.findOffererProfile(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Offerer profile not found for userId: " + userId));
        ProfileSnapshot profile = profileQueryApi.findProfile(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User profile not found for userId: " + userId));

        BigDecimal averageRating = metricsQueryApi.getOffererMainMetrics(userId).averageRating();
        return new OffererSummaryView(
                userId,
                profile.fullName(),
                profile.profilePhotoUrl(),
                offererProfile.specialty(),
                averageRating);
    }

    /**
     * Servicios ACTIVOS del oferente, o lista vacia si aun no publica ninguno. Un oferente sin
     * servicios sigue teniendo un perfil publico valido.
     */
    private List<PublishedServiceView> activeServicesOf(Long userId) {
        List<ServiceSnapshot> services = serviceQueryApi.findByOffererId(userId);
        if (services.isEmpty()) {
            return List.of();
        }
        Map<Long, String> categoryNames = serviceQueryApi.getCategories().stream()
                .collect(Collectors.toMap(CategorySnapshot::id, CategorySnapshot::name));
        return services.stream()
                .filter(ServiceSnapshot::active)
                .map(service -> new PublishedServiceView(
                        service.id(),
                        service.title(),
                        service.description(),
                        service.priceHourly(),
                        categoryNames.get(service.categoryId()),
                        service.averageDurationMinutes()))
                .toList();
    }
}
