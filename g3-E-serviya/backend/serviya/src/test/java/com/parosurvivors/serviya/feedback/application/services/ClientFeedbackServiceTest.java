package com.parosurvivors.serviya.feedback.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.feedback.application.dto.command.SubmitClientFeedbackCommand;
import com.parosurvivors.serviya.feedback.application.mappers.ClientFeedbackCommandMapper;
import com.parosurvivors.serviya.feedback.application.ports.output.ClientFeedbackPersistencePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ClientFeedbackTagCatalogPersistencePort;
import com.parosurvivors.serviya.feedback.application.ports.output.ClientFeedbackTagPersistencePort;
import com.parosurvivors.serviya.feedback.domain.ClientFeedback;
import com.parosurvivors.serviya.feedback.domain.ClientFeedbackTagCatalog;
import com.parosurvivors.serviya.feedback.domain.TagSentiment;
import com.parosurvivors.serviya.requests.api.RequestLookupApi;
import com.parosurvivors.serviya.requests.api.dto.RequestSnapshot;
import com.parosurvivors.serviya.shared.events.application.ports.output.DomainEventPublisherPort;
import com.parosurvivors.serviya.shared.events.domain.ClientFeedbackSubmittedEvent;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
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
class ClientFeedbackServiceTest {

    private static final Long REQUEST_ID = 1L;
    private static final Long SERVICE_ID = 3L;
    private static final Long CLIENT_ID = 10L;
    private static final Long OFFERER_ID = 20L;
    private static final Long FEEDBACK_ID = 5L;
    private static final Long TAG_ID = 7L;

    @Mock private ClientFeedbackPersistencePort clientFeedbackPersistencePort;
    @Mock private ClientFeedbackTagPersistencePort clientFeedbackTagPersistencePort;
    @Mock private ClientFeedbackTagCatalogPersistencePort clientFeedbackTagCatalogPersistencePort;
    @Mock private RequestLookupApi requestLookupApi;
    @Mock private ClientFeedbackCommandMapper commandMapper;
    @Mock private DomainEventPublisherPort eventPublisher;

    @InjectMocks private ClientFeedbackService service;

    @Test
    void submitSucceedsWhenOffererMatchesAndRequestIsCompleted() {
        SubmitClientFeedbackCommand command = new SubmitClientFeedbackCommand(
                OFFERER_ID, REQUEST_ID, CLIENT_ID, 4, "Good", List.of(TAG_ID));
        when(requestLookupApi.findById(REQUEST_ID))
                .thenReturn(Optional.of(request("COMPLETED")));
        when(clientFeedbackPersistencePort.findByRequestId(REQUEST_ID)).thenReturn(Optional.empty());
        when(commandMapper.toFeedback(command)).thenReturn(ClientFeedback.builder()
                .requestId(REQUEST_ID).offererId(OFFERER_ID).clientId(CLIENT_ID)
                .rating(4).comment("Good").build());
        when(clientFeedbackPersistencePort.save(any())).thenReturn(ClientFeedback.builder()
                .id(FEEDBACK_ID).requestId(REQUEST_ID).offererId(OFFERER_ID).clientId(CLIENT_ID)
                .rating(4).comment("Good").build());
        when(clientFeedbackTagCatalogPersistencePort.findAll()).thenReturn(List.of(
                ClientFeedbackTagCatalog.builder()
                        .id(TAG_ID).tagName("Amable").sentiment(TagSentiment.P).build()));

        service.submitClientFeedback(command);

        verify(clientFeedbackPersistencePort).save(any());
        verify(clientFeedbackTagPersistencePort).addTags(FEEDBACK_ID, List.of(TAG_ID));
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(event.capture());
        ClientFeedbackSubmittedEvent submitted = (ClientFeedbackSubmittedEvent) event.getValue();
        assertThat(submitted.requestId()).isEqualTo(REQUEST_ID);
        assertThat(submitted.offererId()).isEqualTo(OFFERER_ID);
        assertThat(submitted.clientId()).isEqualTo(CLIENT_ID);
        assertThat(submitted.tags()).hasSize(1);
        assertThat(submitted.tags().get(0).tagId()).isEqualTo(TAG_ID);
    }

    @Test
    void submitRejectsFeedbackFromAnotherOfferer() {
        when(requestLookupApi.findById(REQUEST_ID))
                .thenReturn(Optional.of(request("COMPLETED", 99L)));

        assertThatThrownBy(() -> service.submitClientFeedback(new SubmitClientFeedbackCommand(
                OFFERER_ID, REQUEST_ID, CLIENT_ID, 4, "Good", List.of())))
                .isInstanceOf(ForbiddenException.class);

        verify(clientFeedbackPersistencePort, never()).save(any());
    }

    @Test
    void submitRejectsRequestThatIsNotCompleted() {
        when(requestLookupApi.findById(REQUEST_ID))
                .thenReturn(Optional.of(request("ACCEPTED")));

        assertThatThrownBy(() -> service.submitClientFeedback(new SubmitClientFeedbackCommand(
                OFFERER_ID, REQUEST_ID, CLIENT_ID, 4, "Good", List.of())))
                .isInstanceOf(InvalidStateException.class);

        verify(clientFeedbackPersistencePort, never()).save(any());
    }

    private RequestSnapshot request(String status) {
        return request(status, OFFERER_ID);
    }

    private RequestSnapshot request(String status, Long offererId) {
        return new RequestSnapshot(
                REQUEST_ID, SERVICE_ID, null, CLIENT_ID, offererId, null,
                null, status, new BigDecimal("25.00"), null, null, null);
    }
}
