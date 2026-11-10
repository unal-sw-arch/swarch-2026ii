package com.parosurvivors.serviya.bff.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.feedback.api.FeedbackQueryApi;
import com.parosurvivors.serviya.feedback.api.dto.TagCatalogEntry;
import com.parosurvivors.serviya.metrics.api.MetricsQueryApi;
import com.parosurvivors.serviya.metrics.api.dto.ClientMetricsSnapshot;
import com.parosurvivors.serviya.metrics.api.dto.OffererMetricsSnapshot;
import com.parosurvivors.serviya.metrics.api.dto.TagMetricSnapshot;
import com.parosurvivors.serviya.requests.api.RequestQueryApi;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricsReadQueryServiceTest {

    private static final Long USER_ID = 10L;

    @Mock private MetricsQueryApi metricsQueryApi;
    @Mock private FeedbackQueryApi feedbackQueryApi;
    @Mock private RequestQueryApi requestQueryApi;

    @InjectMocks private MetricsReadQueryService service;

    @Test
    void offererTagsAreEnrichedFromFeedbackCatalog() {
        when(metricsQueryApi.getOffererTagMetrics(USER_ID)).thenReturn(List.of(
                new TagMetricSnapshot(1L, 3), new TagMetricSnapshot(99L, 1)));
        when(feedbackQueryApi.getServiceTags()).thenReturn(List.of(
                new TagCatalogEntry(1L, "puntual", true)));

        var result = service.getOffererTagMetrics(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).tagName()).isEqualTo("puntual");
        assertThat(result.get(0).positive()).isTrue();
        assertThat(result.get(1).tagName()).isEqualTo("Tag #99");
        assertThat(result.get(1).positive()).isFalse();
    }

    @Test
    void clientMetricsAddActiveRequestCount() {
        when(metricsQueryApi.getClientMetrics(USER_ID)).thenReturn(clientMetrics());
        when(requestQueryApi.countActiveClientRequests(USER_ID)).thenReturn(3L);

        var result = service.getClientMetrics(USER_ID);

        assertThat(result.clientId()).isEqualTo(USER_ID);
        assertThat(result.activeRequests()).isEqualTo(3);
    }

    @Test
    void ownMetricsCombineOffererAndClient() {
        when(metricsQueryApi.getOffererMainMetrics(USER_ID)).thenReturn(offererMetrics());
        when(metricsQueryApi.getClientMainMetrics(USER_ID)).thenReturn(clientMetrics());
        when(requestQueryApi.countActiveClientRequests(USER_ID)).thenReturn(2L);

        var result = service.getOwnMetrics(USER_ID);

        assertThat(result.offererMetrics().averageRating()).isEqualByComparingTo("4.50");
        assertThat(result.clientMetrics().activeRequests()).isEqualTo(2);
    }

    private ClientMetricsSnapshot clientMetrics() {
        return new ClientMetricsSnapshot(USER_ID, new BigDecimal("3.50"), 2, 1, 1, 0,
                5, 4, 3, 1, 1, 1, null);
    }

    private OffererMetricsSnapshot offererMetrics() {
        return new OffererMetricsSnapshot(USER_ID, new BigDecimal("4.50"), 2, 1, 3, 1,
                5, 4, 3, 1, 0, 0, null);
    }
}
