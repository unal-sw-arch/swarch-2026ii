package com.parosurvivors.serviya.bff.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.bff.application.dto.ServiceSearchRequest;
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
import com.parosurvivors.serviya.services.api.dto.ServiceAvailabilitySnapshot;
import com.parosurvivors.serviya.services.api.dto.ServiceSearchCriteria;
import com.parosurvivors.serviya.services.api.dto.ServiceSnapshot;
import com.parosurvivors.serviya.shared.contract.PageResult;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ServiceCatalogQueryServiceTest {

    private static final Long SERVICE_ID = 1L;
    private static final Long OFFERER_ID = 10L;
    private static final Long CLIENT_ID = 20L;
    private static final Long CATEGORY_ID = 3L;

    @Mock private ServiceQueryApi serviceQueryApi;
    @Mock private ProfileQueryApi profileQueryApi;
    @Mock private MetricsQueryApi metricsQueryApi;
    @Mock private FeedbackQueryApi feedbackQueryApi;

    @InjectMocks private ServiceCatalogQueryService service;

    @Test
    void searchResolvesCrossModuleFiltersAndEnrichesResults() {
        var pageable = PageRequest.of(0, 10);
        when(metricsQueryApi.findServiceIdsByAverageRating(4.0, null)).thenReturn(List.of(SERVICE_ID));
        when(profileQueryApi.findUserIdsByFullName("ana")).thenReturn(List.of(OFFERER_ID));
        when(serviceQueryApi.search(any(ServiceSearchCriteria.class), eq(0), eq(10)))
                .thenReturn(new PageResult<>(List.of(service()), 0, 10, 1, 1));
        when(metricsQueryApi.getServiceMetrics(List.of(SERVICE_ID)))
                .thenReturn(Map.of(SERVICE_ID, new ServiceMetricsSnapshot(
                        SERVICE_ID, new BigDecimal("4.50"), 2, 1, 0, null)));
        when(profileQueryApi.findProfiles(List.of(OFFERER_ID)))
                .thenReturn(List.of(profile(OFFERER_ID, "Ana Perez")));

        var result = service.search(new ServiceSearchRequest(
                "ana", null, null, null, null, null, 4.0, null, null, null, null), pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).offererName()).isEqualTo("Ana Perez");
        assertThat(result.getContent().get(0).averageRating()).isEqualByComparingTo("4.50");

        ArgumentCaptor<ServiceSearchCriteria> captor =
                ArgumentCaptor.forClass(ServiceSearchCriteria.class);
        verify(serviceQueryApi).search(captor.capture(), eq(0), eq(10));
        assertThat(captor.getValue().serviceIds()).containsExactly(SERVICE_ID);
        assertThat(captor.getValue().offererIdsByName()).containsExactly(OFFERER_ID);
    }

    @Test
    void searchReturnsEmptyWhenRatingFilterHasNoMatches() {
        when(metricsQueryApi.findServiceIdsByAverageRating(4.0, null)).thenReturn(List.of());

        var result = service.search(new ServiceSearchRequest(
                null, null, null, null, null, null, 4.0, null, null, null, null),
                PageRequest.of(0, 10));

        assertThat(result).isEmpty();
    }

    @Test
    void getDetailComposesServiceOffererMetricsFeedbackAndAvailability() {
        stubDetail();

        var detail = service.getDetail(SERVICE_ID);

        assertThat(detail.id()).isEqualTo(SERVICE_ID);
        assertThat(detail.fullName()).isEqualTo("Ana Perez");
        assertThat(detail.category().name()).isEqualTo("Hogar");
        assertThat(detail.feedbacks()).hasSize(1);
        assertThat(detail.feedbacks().get(0).userName()).isEqualTo("Cliente");
        assertThat(detail.availability()).hasSize(1);
        assertThat(detail.serviceAverageRating()).isEqualByComparingTo("4.50");
    }

    @Test
    void getDetailFailsWhenServiceIsMissingOrDeleted() {
        when(serviceQueryApi.findById(SERVICE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(SERVICE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Servicio no encontrado");
    }

    @Test
    void getByOffererFailsWithoutServices() {
        when(serviceQueryApi.findByOffererId(OFFERER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getByOfferer(OFFERER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no tiene servicios");
    }

    @Test
    void getByOffererMapsCategory() {
        when(serviceQueryApi.findByOffererId(OFFERER_ID)).thenReturn(List.of(service()));
        when(serviceQueryApi.getCategories()).thenReturn(List.of(new CategorySnapshot(CATEGORY_ID, "Hogar")));

        var result = service.getByOfferer(OFFERER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).category().name()).isEqualTo("Hogar");
    }

    private void stubDetail() {
        when(serviceQueryApi.findById(SERVICE_ID)).thenReturn(Optional.of(service()));
        when(serviceQueryApi.findCategory(CATEGORY_ID))
                .thenReturn(Optional.of(new CategorySnapshot(CATEGORY_ID, "Hogar")));
        when(profileQueryApi.findOffererProfile(OFFERER_ID))
                .thenReturn(Optional.of(new OffererProfileSnapshot(
                        2L, OFFERER_ID, "555", "desc", "Plomeria", null, null)));
        when(profileQueryApi.getOffererSummary(OFFERER_ID))
                .thenReturn(Optional.of(new OffererProfileSummarySnapshot(
                        OFFERER_ID, "Ana Perez", "ana.png", "Plomeria")));
        when(metricsQueryApi.getOffererMainMetrics(OFFERER_ID))
                .thenReturn(new OffererMetricsSnapshot(OFFERER_ID, new BigDecimal("4.50"), 2, 1,
                        0, 0, 5, 4, 3, 1, 0, 0, null));
        when(metricsQueryApi.getServiceMetrics(SERVICE_ID))
                .thenReturn(new ServiceMetricsSnapshot(SERVICE_ID, new BigDecimal("4.50"), 2, 1, 0, null));
        when(feedbackQueryApi.getRecentServiceFeedback(SERVICE_ID, 3)).thenReturn(List.of(
                new ServiceFeedbackSnapshot(7L, 99L, SERVICE_ID, CLIENT_ID, 5, "ok",
                        List.of(), LocalDateTime.now())));
        when(profileQueryApi.findProfiles(List.of(CLIENT_ID)))
                .thenReturn(List.of(profile(CLIENT_ID, "Cliente")));
        when(serviceQueryApi.getAvailabilities(SERVICE_ID)).thenReturn(List.of(
                new ServiceAvailabilitySnapshot(1L, SERVICE_ID, 1,
                        LocalTime.of(8, 0), LocalTime.of(17, 0), true),
                new ServiceAvailabilitySnapshot(2L, SERVICE_ID, 2,
                        LocalTime.of(8, 0), LocalTime.of(17, 0), false)));
    }

    private ProfileSnapshot profile(Long userId, String fullName) {
        return new ProfileSnapshot(1L, userId, fullName, "CC", "123", "300",
                null, fullName + ".png", null, "NATURAL", null);
    }

    private ServiceSnapshot service() {
        return new ServiceSnapshot(SERVICE_ID, OFFERER_ID, "Plomeria", "desc", List.of("p.jpg"),
                new BigDecimal("100"), CATEGORY_ID, 60, true, new BigDecimal("10"),
                LocalDateTime.now(), LocalDateTime.now(), null);
    }
}
