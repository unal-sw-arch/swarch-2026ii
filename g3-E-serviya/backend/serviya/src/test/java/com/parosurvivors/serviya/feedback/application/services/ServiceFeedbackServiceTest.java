package com.parosurvivors.serviya.feedback.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.feedback.application.dto.command.SubmitServiceFeedbackCommand;
import com.parosurvivors.serviya.feedback.application.mappers.ServiceFeedbackCommandMapper;
import com.parosurvivors.serviya.feedback.application.ports.output.ServiceFeedbackPersistencePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ServiceFeedbackTagCatalogPersistencePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ServiceFeedbackTagPersistencePort;
import com.parosurvivors.serviya.feedback.domain.ServiceFeedback;
import com.parosurvivors.serviya.feedback.domain.ServiceFeedbackTagCatalog;
import com.parosurvivors.serviya.feedback.domain.TagSentiment;
import com.parosurvivors.serviya.requests.api.RequestLookupApi;
import com.parosurvivors.serviya.requests.api.dto.RequestSnapshot;
import com.parosurvivors.serviya.shared.events.application.ports.output.DomainEventPublisherPort;
import com.parosurvivors.serviya.shared.events.domain.ServiceFeedbackRevertedEvent;
import com.parosurvivors.serviya.shared.events.domain.ServiceFeedbackSubmittedEvent;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.shared.exceptions.ForbiddenException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceFeedbackServiceTest {

    private static final Long REQUEST_ID = 1L;
    private static final Long SERVICE_ID = 3L;
    private static final Long CLIENT_ID = 10L;
    private static final Long OFFERER_ID = 20L;
    private static final Long FEEDBACK_ID = 5L;
    private static final Long TAG_ID = 7L;

    @Mock private ServiceFeedbackPersistencePort serviceFeedbackPersistencePort;
    @Mock private ServiceFeedbackTagPersistencePort serviceFeedbackTagPersistencePort;
    @Mock private ServiceFeedbackTagCatalogPersistencePort serviceFeedbackTagCatalogPersistencePort;
    @Mock private RequestLookupApi requestLookupApi;
    @Mock private ServiceFeedbackCommandMapper commandMapper;
    @Mock private DomainEventPublisherPort eventPublisher;

    @InjectMocks private ServiceFeedbackService service;

    @Test
    void submitSucceedsWhenRequestIsPresumablyCompleted() {
        SubmitServiceFeedbackCommand command = new SubmitServiceFeedbackCommand(
                CLIENT_ID, REQUEST_ID, 5, "Great", List.of(TAG_ID));
        when(requestLookupApi.findById(REQUEST_ID))
                .thenReturn(Optional.of(request("PRESUMABLY_COMPLETED")));
        when(serviceFeedbackPersistencePort.findByRequestId(REQUEST_ID)).thenReturn(Optional.empty());
        when(commandMapper.toFeedback(command)).thenReturn(ServiceFeedback.builder()
                .requestId(REQUEST_ID).clientId(CLIENT_ID).rating(5).comment("Great").build());
        when(serviceFeedbackPersistencePort.save(any())).thenReturn(ServiceFeedback.builder()
                .id(FEEDBACK_ID).requestId(REQUEST_ID).serviceId(SERVICE_ID)
                .clientId(CLIENT_ID).rating(5).comment("Great").build());
        when(serviceFeedbackTagCatalogPersistencePort.findAll()).thenReturn(List.of(
                ServiceFeedbackTagCatalog.builder()
                        .id(TAG_ID).tagName("Puntual").sentiment(TagSentiment.P).build()));

        service.submitServiceFeedback(command);

        verify(serviceFeedbackPersistencePort).save(any());
        verify(serviceFeedbackTagPersistencePort).addTags(FEEDBACK_ID, List.of(TAG_ID));
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(event.capture());
        ServiceFeedbackSubmittedEvent submitted = (ServiceFeedbackSubmittedEvent) event.getValue();
        assertThat(submitted.requestId()).isEqualTo(REQUEST_ID);
        assertThat(submitted.serviceId()).isEqualTo(SERVICE_ID);
        assertThat(submitted.offererId()).isEqualTo(OFFERER_ID);
        assertThat(submitted.clientId()).isEqualTo(CLIENT_ID);
        assertThat(submitted.tags()).hasSize(1);
        assertThat(submitted.tags().get(0).tagId()).isEqualTo(TAG_ID);
    }

    @Test
    void submitRejectsFeedbackFromAnotherClient() {
        when(requestLookupApi.findById(REQUEST_ID))
                .thenReturn(Optional.of(request("COMPLETED", 99L)));

        assertThatThrownBy(() -> service.submitServiceFeedback(new SubmitServiceFeedbackCommand(
                CLIENT_ID, REQUEST_ID, 5, "Great", List.of())))
                .isInstanceOf(ForbiddenException.class);

        verify(serviceFeedbackPersistencePort, never()).save(any());
    }

    @Test
    void submitRejectsRequestThatIsNotCompleted() {
        when(requestLookupApi.findById(REQUEST_ID))
                .thenReturn(Optional.of(request("ACCEPTED")));

        assertThatThrownBy(() -> service.submitServiceFeedback(new SubmitServiceFeedbackCommand(
                CLIENT_ID, REQUEST_ID, 5, "Great", List.of())))
                .isInstanceOf(InvalidStateException.class);

        verify(serviceFeedbackPersistencePort, never()).save(any());
    }

    @Test
    void submitRejectsMissingRequest() {
        when(requestLookupApi.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitServiceFeedback(new SubmitServiceFeedbackCommand(
                CLIENT_ID, REQUEST_ID, 5, "Great", List.of())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void revertPublishesEventWithOffererFromRequestsApi() {
        ServiceFeedback feedback = ServiceFeedback.builder()
                .id(FEEDBACK_ID).requestId(REQUEST_ID).serviceId(SERVICE_ID)
                .clientId(CLIENT_ID).rating(5).comment("Great").build();
        when(serviceFeedbackPersistencePort.findById(FEEDBACK_ID)).thenReturn(Optional.of(feedback));
        when(serviceFeedbackTagPersistencePort.findTagIdsByFeedbackId(FEEDBACK_ID))
                .thenReturn(List.of(TAG_ID));
        when(requestLookupApi.findById(REQUEST_ID)).thenReturn(Optional.of(request("COMPLETED")));
        when(serviceFeedbackTagCatalogPersistencePort.findAll()).thenReturn(List.of(
                ServiceFeedbackTagCatalog.builder()
                        .id(TAG_ID).tagName("Puntual").sentiment(TagSentiment.P).build()));

        boolean reverted = service.revertFeedbackById(FEEDBACK_ID);

        assertThat(reverted).isTrue();
        verify(serviceFeedbackPersistencePort).deleteById(FEEDBACK_ID);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(event.capture());
        ServiceFeedbackRevertedEvent revertedEvent = (ServiceFeedbackRevertedEvent) event.getValue();
        assertThat(revertedEvent.offererId()).isEqualTo(OFFERER_ID);
        assertThat(revertedEvent.requestId()).isEqualTo(REQUEST_ID);
    }

    private RequestSnapshot request(String status) {
        return request(status, CLIENT_ID);
    }

    private RequestSnapshot request(String status, Long clientId) {
        return new RequestSnapshot(
                REQUEST_ID, SERVICE_ID, null, clientId, OFFERER_ID, null,
                null, status, new BigDecimal("25.00"), null, null, null);
    }
}
