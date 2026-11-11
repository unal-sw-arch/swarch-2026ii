package com.parosurvivors.serviya.metrics.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.metrics.api.dto.ClientMetricsSnapshot;
import com.parosurvivors.serviya.metrics.api.dto.OffererMetricsSnapshot;
import com.parosurvivors.serviya.metrics.api.dto.ServiceMetricsSnapshot;
import com.parosurvivors.serviya.metrics.api.dto.TagMetricSnapshot;
import com.parosurvivors.serviya.metrics.application.ports.input.ClientMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.ClientTagMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.OffererMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.OffererTagMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.ServiceMetricsServicePort;
import com.parosurvivors.serviya.metrics.application.ports.input.ServiceTagMetricsServicePort;
import com.parosurvivors.serviya.metrics.domain.ClientMetrics;
import com.parosurvivors.serviya.metrics.domain.ClientTagMetrics;
import com.parosurvivors.serviya.metrics.domain.OffererMetrics;
import com.parosurvivors.serviya.metrics.domain.OffererTagMetrics;
import com.parosurvivors.serviya.metrics.domain.ServiceMetrics;
import com.parosurvivors.serviya.metrics.domain.ServiceTagMetrics;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricsApiFacadeTest {

    @Mock
    private ServiceMetricsServicePort serviceMetricsServicePort;
    @Mock
    private ServiceTagMetricsServicePort serviceTagMetricsServicePort;
    @Mock
    private OffererMetricsServicePort offererMetricsServicePort;
    @Mock
    private OffererTagMetricsServicePort offererTagMetricsServicePort;
    @Mock
    private ClientMetricsServicePort clientMetricsServicePort;
    @Mock
    private ClientTagMetricsServicePort clientTagMetricsServicePort;

    @InjectMocks
    private MetricsApiFacade facade;

    @Test
    void getServiceMetricsMapsFields() {
        ServiceMetrics metrics = ServiceMetrics.builder()
                .serviceId(7L).averageRating(new BigDecimal("4.50"))
                .totalRatings(10).totalComments(4).totalRequestsReceived(12)
                .updatedAt(LocalDateTime.of(2026, 1, 2, 3, 4))
                .build();
        when(serviceMetricsServicePort.getMetrics(7L)).thenReturn(metrics);

        ServiceMetricsSnapshot snapshot = facade.getServiceMetrics(7L);

        assertThat(snapshot.serviceId()).isEqualTo(7L);
        assertThat(snapshot.averageRating()).isEqualByComparingTo("4.50");
        assertThat(snapshot.totalRatings()).isEqualTo(10);
        assertThat(snapshot.totalRequestsReceived()).isEqualTo(12);
    }

    @Test
    void getServiceMetricsBatchMapsMap() {
        when(serviceMetricsServicePort.getMetricsByServiceIds(Set.of(1L, 2L)))
                .thenReturn(Map.of(
                        1L, ServiceMetrics.builder().serviceId(1L).totalRatings(1).build(),
                        2L, ServiceMetrics.builder().serviceId(2L).totalRatings(2).build()));

        Map<Long, ServiceMetricsSnapshot> result = facade.getServiceMetrics(Set.of(1L, 2L));

        assertThat(result).containsOnlyKeys(1L, 2L);
        assertThat(result.get(2L).totalRatings()).isEqualTo(2);
    }

    @Test
    void offererMetricsMap() {
        OffererMetrics metrics = OffererMetrics.builder()
                .offererId(9L).averageRating(new BigDecimal("4.00"))
                .totalRatings(3).totalComments(2).totalPositiveTags(5).totalNegativeTags(1)
                .totalRequestsReceived(8).totalAcceptedRequests(6).totalCompletedServices(4)
                .totalCancelledServices(1).totalRescheduleProposalsSent(2).totalNotProvidedServices(1)
                .build();
        when(offererMetricsServicePort.getAllMetrics(9L)).thenReturn(metrics);
        when(offererMetricsServicePort.getMainMetrics(9L)).thenReturn(metrics);

        OffererMetricsSnapshot all = facade.getOffererMetrics(9L);
        OffererMetricsSnapshot main = facade.getOffererMainMetrics(9L);

        assertThat(all.totalPositiveTags()).isEqualTo(5);
        assertThat(all.totalCompletedServices()).isEqualTo(4);
        assertThat(main.offererId()).isEqualTo(9L);
    }

    @Test
    void clientMetricsMap() {
        ClientMetrics metrics = ClientMetrics.builder()
                .clientId(10L).averageRating(new BigDecimal("3.50"))
                .totalRatings(2).totalComments(1).totalPositiveTags(1).totalNegativeTags(2)
                .totalRequestsSent(5).totalAcceptedRequests(4).totalCompletedRequests(3)
                .totalCancelledRequests(1).totalRescheduledRequests(1).totalNotProvidedRequests(1)
                .build();
        when(clientMetricsServicePort.getAllMetrics(10L)).thenReturn(metrics);
        when(clientMetricsServicePort.getMainMetrics(10L)).thenReturn(metrics);

        ClientMetricsSnapshot all = facade.getClientMetrics(10L);
        ClientMetricsSnapshot main = facade.getClientMainMetrics(10L);

        assertThat(all.totalRequestsSent()).isEqualTo(5);
        assertThat(all.totalRequestsSent()).isEqualTo(5);
        assertThat(main.clientId()).isEqualTo(10L);
    }

    @Test
    void tagMetricsMapCounts() {
        when(serviceTagMetricsServicePort.getTagMetrics(1L)).thenReturn(List.of(
                ServiceTagMetrics.builder().tagId(10L).tagCount(3).build()));
        when(offererTagMetricsServicePort.getTagMetrics(2L)).thenReturn(List.of(
                OffererTagMetrics.builder().tagId(20L).tagCount(4).build()));
        when(clientTagMetricsServicePort.getTagMetrics(3L)).thenReturn(List.of(
                ClientTagMetrics.builder().tagId(30L).tagCount(5).build()));

        List<TagMetricSnapshot> service = facade.getServiceTagMetrics(1L);
        List<TagMetricSnapshot> offerer = facade.getOffererTagMetrics(2L);
        List<TagMetricSnapshot> client = facade.getClientTagMetrics(3L);

        assertThat(service).containsExactly(new TagMetricSnapshot(10L, 3));
        assertThat(offerer).containsExactly(new TagMetricSnapshot(20L, 4));
        assertThat(client).containsExactly(new TagMetricSnapshot(30L, 5));
    }
}
