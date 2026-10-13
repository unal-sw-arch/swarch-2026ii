package com.parosurvivors.serviya.requests.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.requests.application.ports.input.ServiceRequestCommandServicePort;
import com.parosurvivors.serviya.requests.application.ports.output.RescheduleProposalPersistencePort;
import com.parosurvivors.serviya.requests.application.ports.output.RescheduleProposalReadPort;
import com.parosurvivors.serviya.requests.application.ports.output.ServiceRequestPersistencePort;
import com.parosurvivors.serviya.requests.application.ports.output.ServiceRequestReadPort;
import com.parosurvivors.serviya.requests.domain.ProposalStatus;
import com.parosurvivors.serviya.requests.domain.RequestStatus;
import com.parosurvivors.serviya.requests.domain.RescheduleProposal;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;
import com.parosurvivors.serviya.shared.events.application.ports.output.DomainEventPublisherPort;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestMaintenanceServiceTest {

    @Mock ServiceRequestPersistencePort serviceRequestPersistencePort;
    @Mock ServiceRequestReadPort serviceRequestReadPort;
    @Mock ServiceRequestCommandServicePort serviceRequestCommandService;
    @Mock RescheduleProposalReadPort rescheduleProposalReadPort;
    @Mock RescheduleProposalPersistencePort rescheduleProposalPersistencePort;
    @Mock NotificationServicePort notificationServicePort;
    @Mock DomainEventPublisherPort eventPublisher;

    @InjectMocks RequestMaintenanceService service;

    private static final Long CLIENT_ID = 10L;
    private static final Long OFFERER_ID = 20L;
    private static final Long SERVICE_ID = 30L;
    private static final Long REQUEST_ID = 1L;

    @BeforeEach
    void setUp() throws Exception {
        setField("acceptedGraceHours", 24L);
        setField("completionGraceHours", 72L);
    }

    // =====================================================
    // rejectExpiredPendingRequests
    // =====================================================

    @Test
    void rejectExpiredPendingRequests_rejectsExpiredRequests() {
        ServiceRequest expired = pendingRequest();
        when(serviceRequestReadPort.findByStatusAndScheduledDateBefore(
                eq(RequestStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(expired));

        service.rejectExpiredPendingRequests();

        assertThat(expired.getStatus()).isEqualTo(RequestStatus.REJECTED);
        verify(serviceRequestPersistencePort).update(expired);
        verify(eventPublisher).publish(any());
        verify(notificationServicePort).notify(
                eq(CLIENT_ID), eq("request_expired"), eq("Solicitud vencida"),
                eq("Tu solicitud pendiente venció por falta de respuesta del oferente y fue rechazada."),
                eq("SERVICE_REQUEST"), eq(REQUEST_ID), eq(null), eq(Map.of()));
    }

    @Test
    void rejectExpiredPendingRequests_noOp_whenNoExpired() {
        when(serviceRequestReadPort.findByStatusAndScheduledDateBefore(
                eq(RequestStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        service.rejectExpiredPendingRequests();

        verify(serviceRequestPersistencePort, never()).update(any());
        verify(notificationServicePort, never()).notify(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectExpiredPendingRequests_processesAllExpired() {
        ServiceRequest r1 = pendingRequest();
        r1.setId(1L);
        ServiceRequest r2 = pendingRequest();
        r2.setId(2L);

        when(serviceRequestReadPort.findByStatusAndScheduledDateBefore(
                eq(RequestStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(r1, r2));

        service.rejectExpiredPendingRequests();

        assertThat(r1.getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(r2.getStatus()).isEqualTo(RequestStatus.REJECTED);
        verify(serviceRequestPersistencePort).update(r1);
        verify(serviceRequestPersistencePort).update(r2);
    }

    // =====================================================
    // markStaleAcceptedAsNotProvided
    // =====================================================

    @Test
    void markStaleAcceptedAsNotProvided_delegatesToCommandService() {
        ServiceRequest stale = acceptedRequest();
        when(serviceRequestReadPort.findByStatusAndScheduledDateBefore(
                eq(RequestStatus.ACCEPTED), any(LocalDateTime.class)))
                .thenReturn(List.of(stale));

        service.markStaleAcceptedAsNotProvided();

        verify(serviceRequestCommandService).markAsNotProvided(REQUEST_ID, null);
    }

    @Test
    void markStaleAcceptedAsNotProvided_noOp_whenNoStale() {
        when(serviceRequestReadPort.findByStatusAndScheduledDateBefore(
                eq(RequestStatus.ACCEPTED), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        service.markStaleAcceptedAsNotProvided();

        verify(serviceRequestCommandService, never()).markAsNotProvided(any(), any());
    }

    // =====================================================
    // rejectExpiredProposals
    // =====================================================

    @Test
    void rejectExpiredProposals_rejectsExpiredProposals() {
        RescheduleProposal expired = pendingProposal();
        when(rescheduleProposalReadPort.findByStatusAndProposedDateBefore(
                eq(ProposalStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(expired));

        service.rejectExpiredProposals();

        assertThat(expired.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        verify(rescheduleProposalPersistencePort).update(expired);
        verify(notificationServicePort).notify(
                eq(OFFERER_ID), eq("proposal_expired"), eq("Propuesta vencida"),
                eq("Tu propuesta de reprogramación venció y fue rechazada automáticamente."),
                eq("SERVICE_REQUEST"), eq(REQUEST_ID), eq(null), eq(Map.of()));
    }

    @Test
    void rejectExpiredProposals_noOp_whenNoExpired() {
        when(rescheduleProposalReadPort.findByStatusAndProposedDateBefore(
                eq(ProposalStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        service.rejectExpiredProposals();

        verify(rescheduleProposalPersistencePort, never()).update(any());
        verify(notificationServicePort, never()).notify(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    // =====================================================
    // finalizeUnconfirmedCompletions
    // =====================================================

    @Test
    void finalizeUnconfirmedCompletions_autoConfirms() {
        ServiceRequest unconfirmed = presumablyCompletedRequest();
        when(serviceRequestReadPort.findByStatusAndCompletedAtBefore(
                eq(RequestStatus.PRESUMABLY_COMPLETED), any(LocalDateTime.class)))
                .thenReturn(List.of(unconfirmed));

        service.finalizeUnconfirmedCompletions();

        assertThat(unconfirmed.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        verify(serviceRequestPersistencePort).update(unconfirmed);
        verify(eventPublisher).publish(any());
        verify(notificationServicePort).notify(
                eq(CLIENT_ID), eq("auto_completed"), eq("Servicio completado"),
                eq("El servicio se marcó como completado automáticamente por falta de respuesta."),
                eq("SERVICE_REQUEST"), eq(REQUEST_ID), eq(null), eq(Map.of()));
        verify(notificationServicePort).notify(
                eq(OFFERER_ID), eq("auto_completed"), eq("Servicio completado"),
                eq("El servicio se marcó como completado automáticamente por falta de respuesta del cliente."),
                eq("SERVICE_REQUEST"), eq(REQUEST_ID), eq(null), eq(Map.of()));
    }

    @Test
    void finalizeUnconfirmedCompletions_noOp_whenNoneUnconfirmed() {
        when(serviceRequestReadPort.findByStatusAndCompletedAtBefore(
                eq(RequestStatus.PRESUMABLY_COMPLETED), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        service.finalizeUnconfirmedCompletions();

        verify(serviceRequestPersistencePort, never()).update(any());
        verify(notificationServicePort, never()).notify(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private void setField(String fieldName, Object value) throws Exception {
        Field field = RequestMaintenanceService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    private ServiceRequest pendingRequest() {
        return ServiceRequest.builder()
                .id(REQUEST_ID)
                .serviceId(SERVICE_ID)
                .clientId(CLIENT_ID)
                .offererId(OFFERER_ID)
                .scheduledDate(LocalDateTime.of(2026, 7, 1, 14, 0))
                .status(RequestStatus.PENDING)
                .requestedPrice(new BigDecimal("25.00"))
                .createdAt(LocalDateTime.now())
                .updatedStatusAt(LocalDateTime.now())
                .build();
    }

    private ServiceRequest acceptedRequest() {
        ServiceRequest request = pendingRequest();
        request.accept(OFFERER_ID);
        return request;
    }

    private ServiceRequest presumablyCompletedRequest() {
        ServiceRequest request = acceptedRequest();
        request.markAsPresumablyCompleted(OFFERER_ID);
        return request;
    }

    private RescheduleProposal pendingProposal() {
        return RescheduleProposal.builder()
                .id(100L)
                .requestId(REQUEST_ID)
                .clientId(CLIENT_ID)
                .offererId(OFFERER_ID)
                .reason("Conflicto")
                .proposedDate(LocalDateTime.of(2026, 7, 1, 10, 0))
                .status(ProposalStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
