package com.parosurvivors.serviya.statistics.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.feedback.api.FeedbackQueryApi;
import com.parosurvivors.serviya.requests.api.RequestQueryApi;
import com.parosurvivors.serviya.services.api.ServiceQueryApi;
import com.parosurvivors.serviya.services.api.dto.CategorySnapshot;
import com.parosurvivors.serviya.shared.contract.Roles;
import com.parosurvivors.serviya.statistics.application.dto.response.PlatformStatsResponse;
import com.parosurvivors.serviya.users.api.UserQueryApi;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformStatsServiceTest {

    @Mock private UserQueryApi userQueryApi;
    @Mock private RequestQueryApi requestQueryApi;
    @Mock private FeedbackQueryApi feedbackQueryApi;
    @Mock private ServiceQueryApi serviceQueryApi;

    @InjectMocks private PlatformStatsService service;

    @Test
    void composesStatsFromModuleApis() {
        when(userQueryApi.findUserIdsByRole(Roles.OFFERER)).thenReturn(List.of(1L, 2L, 3L));
        when(requestQueryApi.countByStatus("COMPLETED")).thenReturn(10L);
        when(feedbackQueryApi.averageServiceFeedbackRating()).thenReturn(4.5);
        when(serviceQueryApi.getCategories()).thenReturn(List.of(
                new CategorySnapshot(1L, "Hogar"),
                new CategorySnapshot(2L, "Tecnologia")));

        PlatformStatsResponse response = service.getPlatformStats();

        assertThat(response.activeOfferers()).isEqualTo(3L);
        assertThat(response.completedServices()).isEqualTo(10L);
        assertThat(response.averageRating()).isEqualTo(4.5);
        assertThat(response.totalCategories()).isEqualTo(2L);
    }

    @Test
    void clampsAverageRatingToRange() {
        when(userQueryApi.findUserIdsByRole(Roles.OFFERER)).thenReturn(List.of());
        when(requestQueryApi.countByStatus("COMPLETED")).thenReturn(0L);
        when(serviceQueryApi.getCategories()).thenReturn(List.of());
        when(feedbackQueryApi.averageServiceFeedbackRating()).thenReturn(7.0);

        assertThat(service.getPlatformStats().averageRating()).isEqualTo(5.0);
    }

    @Test
    void returnsZerosWhenADependencyFails() {
        when(userQueryApi.findUserIdsByRole(Roles.OFFERER))
                .thenThrow(new IllegalStateException("users down"));

        PlatformStatsResponse response = service.getPlatformStats();

        assertThat(response.activeOfferers()).isZero();
        assertThat(response.completedServices()).isZero();
        assertThat(response.averageRating()).isZero();
        assertThat(response.totalCategories()).isZero();
    }
}
