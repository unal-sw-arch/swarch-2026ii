package com.parosurvivors.serviya.feedback.application.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.feedback.api.dto.ClientFeedbackSnapshot;
import com.parosurvivors.serviya.feedback.api.dto.ServiceFeedbackSnapshot;
import com.parosurvivors.serviya.feedback.application.ports.input.ClientFeedbackServicePort;
import com.parosurvivors.serviya.feedback.application.ports.input.ServiceFeedbackServicePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ClientFeedbackPersistencePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ClientFeedbackTagCatalogPersistencePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ClientFeedbackTagPersistencePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ServiceFeedbackPersistencePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ServiceFeedbackTagCatalogPersistencePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ServiceFeedbackTagPersistencePort;
import com.parosurvivors.serviya.feedback.domain.ClientFeedback;
import com.parosurvivors.serviya.feedback.domain.ClientFeedbackTagCatalog;
import com.parosurvivors.serviya.feedback.domain.ServiceFeedback;
import com.parosurvivors.serviya.feedback.domain.ServiceFeedbackTagCatalog;
import com.parosurvivors.serviya.feedback.domain.TagSentiment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class FeedbackApiFacadeTest {

    @Mock
    private ServiceFeedbackPersistencePort serviceFeedbackPersistencePort;
    @Mock
    private ServiceFeedbackTagPersistencePort serviceFeedbackTagPersistencePort;
    @Mock
    private ServiceFeedbackTagCatalogPersistencePort serviceFeedbackTagCatalogPersistencePort;
    @Mock
    private ClientFeedbackPersistencePort clientFeedbackPersistencePort;
    @Mock
    private ClientFeedbackTagPersistencePort clientFeedbackTagPersistencePort;
    @Mock
    private ClientFeedbackTagCatalogPersistencePort clientFeedbackTagCatalogPersistencePort;
    @Mock
    private ServiceFeedbackServicePort serviceFeedbackServicePort;
    @Mock
    private ClientFeedbackServicePort clientFeedbackServicePort;

    @InjectMocks
    private FeedbackApiFacade facade;

    @Test
    void findServiceFeedbackMapsFieldsAndTagIds() {
        ServiceFeedback feedback = ServiceFeedback.builder()
                .id(1L).requestId(2L).serviceId(3L).clientId(4L)
                .rating(5).comment("Great").createdAt(LocalDateTime.of(2026, 1, 2, 3, 4))
                .build();
        when(serviceFeedbackPersistencePort.findById(1L)).thenReturn(Optional.of(feedback));
        when(serviceFeedbackTagPersistencePort.findTagIdsByFeedbackId(1L))
                .thenReturn(List.of(10L, 11L));

        ServiceFeedbackSnapshot snapshot = facade.findServiceFeedback(1L).orElseThrow();

        assertThat(snapshot.requestId()).isEqualTo(2L);
        assertThat(snapshot.serviceId()).isEqualTo(3L);
        assertThat(snapshot.rating()).isEqualTo(5);
        assertThat(snapshot.tagIds()).containsExactly(10L, 11L);
    }

    @Test
    void findClientFeedbackMapsFieldsAndTagIds() {
        ClientFeedback feedback = ClientFeedback.builder()
                .id(1L).requestId(2L).offererId(9L).clientId(4L)
                .rating(4).comment("Good").build();
        when(clientFeedbackPersistencePort.findById(1L)).thenReturn(Optional.of(feedback));
        when(clientFeedbackTagPersistencePort.findTagIdsByFeedbackId(1L))
                .thenReturn(List.of(20L));

        ClientFeedbackSnapshot snapshot = facade.findClientFeedback(1L).orElseThrow();

        assertThat(snapshot.offererId()).isEqualTo(9L);
        assertThat(snapshot.clientId()).isEqualTo(4L);
        assertThat(snapshot.tagIds()).containsExactly(20L);
    }

    @Test
    void findFeedbackReturnsEmptyWhenMissing() {
        when(serviceFeedbackPersistencePort.findById(99L)).thenReturn(Optional.empty());
        when(clientFeedbackPersistencePort.findById(99L)).thenReturn(Optional.empty());

        assertThat(facade.findServiceFeedback(99L)).isEmpty();
        assertThat(facade.findClientFeedback(99L)).isEmpty();
    }

    @Test
    void getRecentServiceFeedbackMapsList() {
        ServiceFeedback first = ServiceFeedback.builder().id(1L).requestId(2L).build();
        ServiceFeedback second = ServiceFeedback.builder().id(3L).requestId(4L).build();
        when(serviceFeedbackPersistencePort.findRecentByServiceId(7L, 5))
                .thenReturn(List.of(first, second));
        when(serviceFeedbackTagPersistencePort.findTagIdsByFeedbackId(1L)).thenReturn(List.of());
        when(serviceFeedbackTagPersistencePort.findTagIdsByFeedbackId(3L)).thenReturn(List.of(30L));

        List<ServiceFeedbackSnapshot> result = facade.getRecentServiceFeedback(7L, 5);

        assertThat(result).hasSize(2);
        assertThat(result.get(1).tagIds()).containsExactly(30L);
    }

    @Test
    void existenceChecksDelegate() {
        when(serviceFeedbackPersistencePort.existsByRequestId(2L)).thenReturn(true);
        when(clientFeedbackPersistencePort.existsByRequestId(3L)).thenReturn(false);

        assertThat(facade.existsServiceFeedbackForRequest(2L)).isTrue();
        assertThat(facade.existsClientFeedbackForRequest(3L)).isFalse();
    }

    @Test
    void tagCatalogsMapSentiment() {
        ServiceFeedbackTagCatalog serviceTag = ServiceFeedbackTagCatalog.builder()
                .id(1L).tagName("Puntual").sentiment(TagSentiment.P).build();
        ClientFeedbackTagCatalog clientTag = ClientFeedbackTagCatalog.builder()
                .id(2L).tagName("Impuntual").sentiment(TagSentiment.N).build();
        when(serviceFeedbackTagCatalogPersistencePort.findAll()).thenReturn(List.of(serviceTag));
        when(clientFeedbackTagCatalogPersistencePort.findAll()).thenReturn(List.of(clientTag));

        assertThat(facade.getServiceTags().get(0).positive()).isTrue();
        assertThat(facade.getClientTags().get(0).positive()).isFalse();
    }

    @Test
    void averageServiceFeedbackRatingDefaultsToZero() {
        when(serviceFeedbackPersistencePort.findAverageRating()).thenReturn(null);
        assertThat(facade.averageServiceFeedbackRating()).isZero();

        when(serviceFeedbackPersistencePort.findAverageRating()).thenReturn(4.25);
        assertThat(facade.averageServiceFeedbackRating()).isEqualTo(4.25);
    }

    @Test
    void revertCommandsDelegate() {
        when(serviceFeedbackServicePort.revertFeedbackById(1L)).thenReturn(true);
        when(clientFeedbackServicePort.revertFeedbackById(2L)).thenReturn(false);

        assertThat(facade.revertServiceFeedback(1L)).isTrue();
        assertThat(facade.revertClientFeedback(2L)).isFalse();

        verify(serviceFeedbackServicePort).revertFeedbackById(1L);
        verify(clientFeedbackServicePort).revertFeedbackById(2L);
    }

    @Test
    void searchServiceFeedbackMapsPage() {
        ServiceFeedback feedback = ServiceFeedback.builder()
                .id(1L).requestId(2L).serviceId(3L).clientId(4L).rating(5).build();
        when(serviceFeedbackPersistencePort.findByServiceIdAndFilters(
                3L, "x", 4, 5, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(feedback)));

        var result = facade.searchServiceFeedback(3L, null, "x", 4, 5, 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).serviceId()).isEqualTo(3L);
    }

    @Test
    void searchClientFeedbackReturnsEmptyWithoutFilters() {
        var result = facade.searchClientFeedback(null, null, null, null, null, 0, 10);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }
}
