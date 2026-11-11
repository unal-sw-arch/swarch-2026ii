package com.parosurvivors.serviya.bff.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicOffererQueryServiceTest {

    private static final Long OFFERER_ID = 7L;

    @Mock private ProfileQueryApi profileQueryApi;
    @Mock private MetricsQueryApi metricsQueryApi;
    @Mock private ServiceQueryApi serviceQueryApi;

    @InjectMocks private PublicOffererQueryService service;

    @Test
    void publicProfileCombinesProfileMetricsAndActiveServices() {
        when(profileQueryApi.findOffererProfile(OFFERER_ID))
                .thenReturn(Optional.of(new OffererProfileSnapshot(
                        1L, OFFERER_ID, "555", "desc", "Plomeria", null, null)));
        when(profileQueryApi.findProfile(OFFERER_ID))
                .thenReturn(Optional.of(profile()));
        when(metricsQueryApi.getOffererMainMetrics(OFFERER_ID)).thenReturn(metrics());
        when(serviceQueryApi.findByOffererId(OFFERER_ID)).thenReturn(List.of(
                service(1L, true, 10L),
                service(2L, false, 10L),
                service(3L, true, 99L)));
        when(serviceQueryApi.getCategories()).thenReturn(List.of(
                new CategorySnapshot(10L, "Hogar")));

        var view = service.getPublicProfile(OFFERER_ID);

        assertThat(view.fullName()).isEqualTo("Ana Perez");
        assertThat(view.averageRating()).isEqualByComparingTo("4.50");
        assertThat(view.services()).hasSize(2);
        assertThat(view.services().get(0).categoryName()).isEqualTo("Hogar");
        assertThat(view.services().get(1).categoryName()).isNull();
    }

    @Test
    void publicProfileReturnsEmptyServicesWithoutServices() {
        when(profileQueryApi.findOffererProfile(OFFERER_ID))
                .thenReturn(Optional.of(new OffererProfileSnapshot(
                        1L, OFFERER_ID, "555", "desc", "Plomeria", null, null)));
        when(profileQueryApi.findProfile(OFFERER_ID)).thenReturn(Optional.of(profile()));
        when(metricsQueryApi.getOffererMainMetrics(OFFERER_ID)).thenReturn(metrics());
        when(serviceQueryApi.findByOffererId(OFFERER_ID)).thenReturn(List.of());

        assertThat(service.getPublicProfile(OFFERER_ID).services()).isEmpty();
    }

    @Test
    void publicProfileFailsWhenOffererProfileIsMissing() {
        when(profileQueryApi.findOffererProfile(OFFERER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublicProfile(OFFERER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Offerer profile");
    }

    @Test
    void summaryCombinesProfileWithAverageRating() {
        when(profileQueryApi.findOffererProfile(OFFERER_ID))
                .thenReturn(Optional.of(new OffererProfileSnapshot(
                        1L, OFFERER_ID, "555", "desc", "Plomeria", null, null)));
        when(profileQueryApi.findProfile(OFFERER_ID)).thenReturn(Optional.of(profile()));
        when(metricsQueryApi.getOffererMainMetrics(OFFERER_ID)).thenReturn(metrics());

        var summary = service.getSummary(OFFERER_ID);

        assertThat(summary.fullName()).isEqualTo("Ana Perez");
        assertThat(summary.specialty()).isEqualTo("Plomeria");
        assertThat(summary.averageRating()).isEqualByComparingTo("4.50");
    }

    private ProfileSnapshot profile() {
        return new ProfileSnapshot(1L, OFFERER_ID, "Ana Perez", "CC", "123", "300",
                1L, "photo.png", "bio", "NATURAL", null);
    }

    private OffererMetricsSnapshot metrics() {
        return new OffererMetricsSnapshot(OFFERER_ID, new BigDecimal("4.50"), 2, 1, 3, 1,
                5, 4, 3, 1, 0, 0, null);
    }

    private ServiceSnapshot service(Long id, boolean active, Long categoryId) {
        return new ServiceSnapshot(id, OFFERER_ID, "Servicio " + id, "desc", List.of(),
                new BigDecimal("100"), categoryId, 60, active, null, null, null, null);
    }
}
