package com.parosurvivors.serviya.requests.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.profiles.api.ProfileQueryApi;
import com.parosurvivors.serviya.profiles.api.dto.AddressSnapshot;
import com.parosurvivors.serviya.profiles.api.dto.ProfileSnapshot;
import com.parosurvivors.serviya.requests.application.dto.command.CreateServiceRequestCommand;
import com.parosurvivors.serviya.requests.application.mappers.ServiceRequestCommandMapper;
import com.parosurvivors.serviya.requests.application.ports.input.RescheduleProposalServicePort;
import com.parosurvivors.serviya.requests.application.ports.output.ServiceRequestPersistencePort;
import com.parosurvivors.serviya.requests.application.ports.output.ServiceRequestReadPort;
import com.parosurvivors.serviya.requests.domain.RequestStatus;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;
import com.parosurvivors.serviya.services.api.ServiceQueryApi;
import com.parosurvivors.serviya.services.api.dto.ServiceAvailabilitySnapshot;
import com.parosurvivors.serviya.services.api.dto.ServiceSnapshot;
import com.parosurvivors.serviya.shared.events.application.ChatEventPublisher;
import com.parosurvivors.serviya.shared.events.application.ports.output.DomainEventPublisherPort;
import com.parosurvivors.serviya.shared.exceptions.BusinessRuleException;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.shared.exceptions.ForbiddenException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceRequestCommandServiceTest {

    @Mock ServiceRequestPersistencePort serviceRequestPersistencePort;
    @Mock ServiceRequestReadPort serviceRequestReadPort;
    @Mock RescheduleProposalServicePort rescheduleProposalService;
    @Mock NotificationServicePort notificationServicePort;
    @Mock ServiceRequestCommandMapper commandMapper;
    @Mock ServiceQueryApi serviceQueryApi;
    @Mock ProfileQueryApi profileQueryApi;
    @Mock DomainEventPublisherPort eventPublisher;
    @Mock ChatEventPublisher chatEventPublisher;

    @InjectMocks ServiceRequestCommandService service;

    private static final Long REQUEST_ID = 1L;
    private static final Long CLIENT_ID = 10L;
    private static final Long OFFERER_ID = 20L;
    private static final Long SERVICE_ID = 30L;
    private static final Long ADDRESS_ID = 40L;

    // =====================================================
    // createRequest
    // =====================================================

    @Test
    void createRequest_succeeds_withValidCommand() {
        CreateServiceRequestCommand command = createCommand();
        ServiceSnapshot domainService = existingService();
        ServiceRequest unmapped = ServiceRequest.builder().clientId(CLIENT_ID).serviceId(SERVICE_ID).build();
        ServiceRequest saved = pendingRequest();

        when(serviceQueryApi.findById(SERVICE_ID)).thenReturn(Optional.of(domainService));
        when(serviceQueryApi.getAvailabilities(SERVICE_ID)).thenReturn(List.of(
                availability(1, LocalTime.of(8, 0), LocalTime.of(18, 0))));
        when(commandMapper.toDomain(command)).thenReturn(unmapped);
        when(serviceRequestPersistencePort.save(any())).thenReturn(saved);
        when(profileQueryApi.findProfile(CLIENT_ID)).thenReturn(Optional.of(clientProfile()));

        ServiceRequest result = service.createRequest(command);

        assertThat(result).isSameAs(saved);
        verify(eventPublisher).publish(any());
        verify(notificationServicePort).notify(
                eq(OFFERER_ID), eq("new_request"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createRequest_throws_whenServiceNotFound() {
        when(serviceQueryApi.findById(SERVICE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRequest(createCommand()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Servicio no encontrado");

        verify(serviceRequestPersistencePort, never()).save(any());
    }

    @Test
    void createRequest_throws_whenServiceUnavailable() {
        when(serviceQueryApi.findById(SERVICE_ID)).thenReturn(Optional.of(existingService()));
        // Martes (dayOfWeek=2 → index 2) pero disponibilidad es solo lunes (index 1)
        when(serviceQueryApi.getAvailabilities(SERVICE_ID)).thenReturn(List.of(
                availability(1, LocalTime.of(8, 0), LocalTime.of(18, 0))));

        CreateServiceRequestCommand command = new CreateServiceRequestCommand(
                CLIENT_ID, SERVICE_ID, ADDRESS_ID, LocalDateTime.of(2026, 8, 4, 14, 0)); // martes

        assertThatThrownBy(() -> service.createRequest(command))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("disponible");
    }

    @Test
    void createRequest_succeeds_whenNoAvailabilitiesConfigured() {
        ServiceRequest unmapped = ServiceRequest.builder().clientId(CLIENT_ID).serviceId(SERVICE_ID).build();
        when(serviceQueryApi.findById(SERVICE_ID)).thenReturn(Optional.of(existingService()));
        when(serviceQueryApi.getAvailabilities(SERVICE_ID)).thenReturn(Collections.emptyList());
        when(commandMapper.toDomain(createCommand())).thenReturn(unmapped);
        when(serviceRequestPersistencePort.save(any())).thenReturn(pendingRequest());
        when(profileQueryApi.findProfile(CLIENT_ID)).thenReturn(Optional.of(clientProfile()));

        service.createRequest(createCommand());

        verify(serviceRequestPersistencePort).save(any());
    }

    @Test
    void createRequest_throws_whenAddressOutsideRadius() {
        ServiceSnapshot svc = existingService(new BigDecimal("5.0"));
        when(serviceQueryApi.findById(SERVICE_ID)).thenReturn(Optional.of(svc));
        when(serviceQueryApi.getAvailabilities(SERVICE_ID)).thenReturn(Collections.emptyList());

        AddressSnapshot clientAddress = new AddressSnapshot(
                ADDRESS_ID, CLIENT_ID, null, null,
                new BigDecimal("10.0"), new BigDecimal("-75.0"), null);
        when(profileQueryApi.findAddress(ADDRESS_ID)).thenReturn(Optional.of(clientAddress));

        ProfileSnapshot offererProfile = profile(OFFERER_ID, null, 50L);
        when(profileQueryApi.findProfile(OFFERER_ID)).thenReturn(Optional.of(offererProfile));

        // Offerer address far away (different city)
        AddressSnapshot offererAddress = new AddressSnapshot(
                50L, OFFERER_ID, null, null,
                new BigDecimal("0.0"), new BigDecimal("0.0"), null);
        when(profileQueryApi.findAddress(50L)).thenReturn(Optional.of(offererAddress));

        assertThatThrownBy(() -> service.createRequest(createCommand()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("radio de operación");
    }

    // =====================================================
    // checkServiceAvailability
    // =====================================================

    @Test
    void checkServiceAvailability_returnsTrue_whenNoAvailabilities() {
        when(serviceQueryApi.getAvailabilities(SERVICE_ID)).thenReturn(Collections.emptyList());

        boolean result = service.checkServiceAvailability(SERVICE_ID, LocalDateTime.of(2026, 8, 1, 14, 0));

        assertThat(result).isTrue();
    }

    @Test
    void checkServiceAvailability_returnsTrue_whenWithinWindow() {
        when(serviceQueryApi.getAvailabilities(SERVICE_ID)).thenReturn(List.of(
                availability(1, LocalTime.of(8, 0), LocalTime.of(18, 0))));
        // lunes 14:00 — dentro de la ventana
        boolean result = service.checkServiceAvailability(SERVICE_ID, LocalDateTime.of(2026, 8, 3, 14, 0));

        assertThat(result).isTrue();
    }

    @Test
    void checkServiceAvailability_throws_whenOutsideWindow() {
        when(serviceQueryApi.getAvailabilities(SERVICE_ID)).thenReturn(List.of(
                availability(1, LocalTime.of(8, 0), LocalTime.of(18, 0))));
        // lunes 20:00 — fuera de la ventana
        assertThatThrownBy(() ->
                service.checkServiceAvailability(SERVICE_ID, LocalDateTime.of(2026, 8, 3, 20, 0)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void checkServiceAvailability_ignoresInactiveAvailability() {
        ServiceAvailabilitySnapshot inactive = availability(1, LocalTime.of(8, 0), LocalTime.of(18, 0), false);
        when(serviceQueryApi.getAvailabilities(SERVICE_ID)).thenReturn(List.of(inactive));

        assertThatThrownBy(() ->
                service.checkServiceAvailability(SERVICE_ID, LocalDateTime.of(2026, 8, 3, 14, 0)))
                .isInstanceOf(BusinessRuleException.class);
    }

    // =====================================================
    // checkWithinRadius
    // =====================================================

    @Test
    void checkWithinRadius_returnsTrue_whenNoRadiusSet() {
        ServiceSnapshot svc = existingService(null);
        when(serviceQueryApi.findById(SERVICE_ID)).thenReturn(Optional.of(svc));

        assertThat(service.checkWithinRadius(SERVICE_ID, ADDRESS_ID)).isTrue();
    }

    @Test
    void checkWithinRadius_returnsTrue_whenRadiusIsZero() {
        ServiceSnapshot svc = existingService(BigDecimal.ZERO);
        when(serviceQueryApi.findById(SERVICE_ID)).thenReturn(Optional.of(svc));

        assertThat(service.checkWithinRadius(SERVICE_ID, ADDRESS_ID)).isTrue();
    }

    @Test
    void checkWithinRadius_throws_whenOffererHasNoAddress() {
        ServiceSnapshot svc = existingService(new BigDecimal("10.0"));
        when(serviceQueryApi.findById(SERVICE_ID)).thenReturn(Optional.of(svc));
        when(profileQueryApi.findAddress(ADDRESS_ID)).thenReturn(Optional.of(
                new AddressSnapshot(ADDRESS_ID, CLIENT_ID, null, null,
                        BigDecimal.ZERO, BigDecimal.ZERO, null)));
        when(profileQueryApi.findProfile(OFFERER_ID)).thenReturn(
                Optional.of(profile(OFFERER_ID, null, null)));

        assertThatThrownBy(() -> service.checkWithinRadius(SERVICE_ID, ADDRESS_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dirección principal");
    }

    // =====================================================
    // acceptRequest
    // =====================================================

    @Test
    void acceptRequest_succeeds_whenOffererOwnsRequest() {
        ServiceRequest request = pendingRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(profileQueryApi.findProfile(OFFERER_ID)).thenReturn(Optional.of(offererProfile()));

        service.acceptRequest(REQUEST_ID, OFFERER_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.ACCEPTED);
        verify(serviceRequestPersistencePort).update(request);
        verify(eventPublisher).publish(any());
    }

    @Test
    void acceptRequest_throws_whenNotOfferer() {
        ServiceRequest request = pendingRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.acceptRequest(REQUEST_ID, CLIENT_ID))
                .isInstanceOf(ForbiddenException.class);

        verify(serviceRequestPersistencePort, never()).update(any());
    }

    @Test
    void acceptRequest_throws_whenRequestNotFound() {
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptRequest(REQUEST_ID, OFFERER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =====================================================
    // rejectRequest
    // =====================================================

    @Test
    void rejectRequest_succeeds_whenOffererOwnsRequest() {
        ServiceRequest request = pendingRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(profileQueryApi.findProfile(OFFERER_ID)).thenReturn(Optional.of(offererProfile()));

        service.rejectRequest(REQUEST_ID, OFFERER_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.REJECTED);
        verify(serviceRequestPersistencePort).update(request);
        verify(eventPublisher).publish(any());
    }

    @Test
    void rejectRequest_throws_whenNotOfferer() {
        ServiceRequest request = pendingRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.rejectRequest(REQUEST_ID, CLIENT_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    // =====================================================
    // cancelRequest
    // =====================================================

    @Test
    void cancelRequest_succeeds_whenCancelledByClient() {
        ServiceRequest request = pendingRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        service.cancelRequest(REQUEST_ID, CLIENT_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        verify(rescheduleProposalService).cancelPendingProposals(REQUEST_ID);
        verify(serviceRequestPersistencePort).update(request);
        verify(notificationServicePort).notify(
                eq(OFFERER_ID), eq("request_cancelled"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void cancelRequest_succeeds_whenCancelledByOfferer() {
        ServiceRequest request = pendingRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        service.cancelRequest(REQUEST_ID, OFFERER_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        verify(notificationServicePort).notify(
                eq(CLIENT_ID), eq("request_cancelled"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void cancelRequest_throws_whenNotParticipant() {
        ServiceRequest request = pendingRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancelRequest(REQUEST_ID, 999L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no participa");
    }

    // =====================================================
    // markAsPresumablyCompleted
    // =====================================================

    @Test
    void markAsPresumablyCompleted_succeeds() {
        ServiceRequest request = acceptedRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(profileQueryApi.findProfile(OFFERER_ID)).thenReturn(Optional.of(offererProfile()));

        service.markAsPresumablyCompleted(REQUEST_ID, OFFERER_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.PRESUMABLY_COMPLETED);
        verify(rescheduleProposalService).cancelPendingProposals(REQUEST_ID);
        verify(serviceRequestPersistencePort).update(request);
    }

    @Test
    void markAsPresumablyCompleted_throws_whenNotOfferer() {
        ServiceRequest request = acceptedRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.markAsPresumablyCompleted(REQUEST_ID, CLIENT_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    // =====================================================
    // confirmCompletion
    // =====================================================

    @Test
    void confirmCompletion_succeeds() {
        ServiceRequest request = presumablyCompletedRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(profileQueryApi.findProfile(CLIENT_ID)).thenReturn(Optional.of(clientProfile()));

        service.confirmCompletion(REQUEST_ID, CLIENT_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        verify(serviceRequestPersistencePort).update(request);
    }

    @Test
    void confirmCompletion_throws_whenNotClient() {
        ServiceRequest request = presumablyCompletedRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.confirmCompletion(REQUEST_ID, OFFERER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    // =====================================================
    // markAsNotProvided
    // =====================================================

    @Test
    void markAsNotProvided_setsNotProvided_whenNoPendingProposals() {
        ServiceRequest request = acceptedRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(rescheduleProposalService.cancelPendingProposals(REQUEST_ID)).thenReturn(0);

        service.markAsNotProvided(REQUEST_ID, OFFERER_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.NOT_PROVIDED);
        verify(serviceRequestPersistencePort).update(request);
        verify(notificationServicePort, org.mockito.Mockito.times(2)).notify(
                any(), eq("service_not_provided"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void markAsNotProvided_cancelsInstead_whenPendingProposalsExist() {
        ServiceRequest request = acceptedRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(rescheduleProposalService.cancelPendingProposals(REQUEST_ID)).thenReturn(1);

        service.markAsNotProvided(REQUEST_ID, OFFERER_ID);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        verify(serviceRequestPersistencePort).update(request);
    }

    // =====================================================
    // cancelActiveRequestsForUser
    // =====================================================

    @Test
    void cancelActiveRequestsForUser_cancelsAllActiveRequests() {
        ServiceRequest r1 = pendingRequest();
        r1.setId(1L);
        ServiceRequest r2 = acceptedRequest();
        r2.setId(2L);

        when(serviceRequestReadPort.findByParticipantAndStatusIn(
                CLIENT_ID, List.of(RequestStatus.PENDING, RequestStatus.ACCEPTED)))
                .thenReturn(List.of(r1, r2));
        // Las solicitudes internas necesitan findById para cancelRequest
        when(serviceRequestReadPort.findById(1L)).thenReturn(Optional.of(r1));
        when(serviceRequestReadPort.findById(2L)).thenReturn(Optional.of(r2));

        List<ServiceRequest> cancelled = service.cancelActiveRequestsForUser(CLIENT_ID);

        assertThat(cancelled).hasSize(2);
        assertThat(r1.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(r2.getStatus()).isEqualTo(RequestStatus.CANCELLED);
    }

    @Test
    void cancelActiveRequestsForUser_returnsEmpty_whenNoActive() {
        when(serviceRequestReadPort.findByParticipantAndStatusIn(
                CLIENT_ID, List.of(RequestStatus.PENDING, RequestStatus.ACCEPTED)))
                .thenReturn(Collections.emptyList());

        List<ServiceRequest> cancelled = service.cancelActiveRequestsForUser(CLIENT_ID);

        assertThat(cancelled).isEmpty();
    }

    // =====================================================
    // cancelActiveRequestsForRole
    // =====================================================

    @Test
    void cancelActiveRequestsForRole_onlyCancelsAsOfferer() {
        ServiceRequest asClient = pendingRequest();
        asClient.setId(1L);
        asClient.setOffererId(99L);
        ServiceRequest asOfferer = acceptedRequest();
        asOfferer.setId(2L);

        when(serviceRequestReadPort.findByParticipantAndStatusIn(
                OFFERER_ID, List.of(RequestStatus.PENDING, RequestStatus.ACCEPTED)))
                .thenReturn(List.of(asClient, asOfferer));
        when(serviceRequestReadPort.findById(2L)).thenReturn(Optional.of(asOfferer));

        List<ServiceRequest> cancelled = service.cancelActiveRequestsForRole(OFFERER_ID, true);

        assertThat(cancelled).hasSize(1);
        assertThat(asOfferer.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(asClient.getStatus()).isEqualTo(RequestStatus.PENDING);
    }

    @Test
    void cancelActiveRequestsForRole_onlyCancelsAsClient() {
        ServiceRequest asClient = pendingRequest();
        asClient.setId(1L);
        ServiceRequest asOfferer = acceptedRequest();
        asOfferer.setId(2L);
        asOfferer.setClientId(99L);

        when(serviceRequestReadPort.findByParticipantAndStatusIn(
                CLIENT_ID, List.of(RequestStatus.PENDING, RequestStatus.ACCEPTED)))
                .thenReturn(List.of(asClient, asOfferer));
        when(serviceRequestReadPort.findById(1L)).thenReturn(Optional.of(asClient));

        List<ServiceRequest> cancelled = service.cancelActiveRequestsForRole(CLIENT_ID, false);

        assertThat(cancelled).hasSize(1);
        assertThat(asClient.getStatus()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(asOfferer.getStatus()).isEqualTo(RequestStatus.ACCEPTED);
    }

    // =====================================================
    // rescheduleRequest
    // =====================================================

    @Test
    void rescheduleRequest_createsReplacement() {
        ServiceRequest request = pendingRequest();
        request.setId(REQUEST_ID);
        LocalDateTime newDate = LocalDateTime.of(2026, 9, 1, 10, 0);
        ServiceRequest replacement = ServiceRequest.builder()
                .id(2L).previousRequestId(REQUEST_ID).scheduledDate(newDate)
                .status(RequestStatus.PENDING).clientId(CLIENT_ID).offererId(OFFERER_ID)
                .serviceId(SERVICE_ID).build();

        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(serviceRequestPersistencePort.save(any())).thenReturn(replacement);
        when(profileQueryApi.findProfile(CLIENT_ID)).thenReturn(Optional.of(clientProfile()));

        ServiceRequest result = service.rescheduleRequest(REQUEST_ID, newDate, CLIENT_ID);

        assertThat(result).isSameAs(replacement);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.RESCHEDULED);
        verify(rescheduleProposalService).supersedePendingProposals(REQUEST_ID);
        verify(serviceRequestPersistencePort).update(request);
        verify(serviceRequestPersistencePort).save(any());
    }

    @Test
    void rescheduleRequest_throws_whenNotClient() {
        ServiceRequest request = pendingRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.rescheduleRequest(
                REQUEST_ID, LocalDateTime.of(2026, 9, 1, 10, 0), OFFERER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rescheduleRequest_throws_whenRequestNotFound() {
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rescheduleRequest(
                REQUEST_ID, LocalDateTime.of(2026, 9, 1, 10, 0), CLIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private CreateServiceRequestCommand createCommand() {
        return new CreateServiceRequestCommand(
                CLIENT_ID, SERVICE_ID, ADDRESS_ID, LocalDateTime.of(2026, 8, 3, 14, 0));
    }

    private ServiceRequest pendingRequest() {
        return ServiceRequest.builder()
                .id(REQUEST_ID)
                .serviceId(SERVICE_ID)
                .clientId(CLIENT_ID)
                .offererId(OFFERER_ID)
                .addressId(ADDRESS_ID)
                .scheduledDate(LocalDateTime.of(2026, 8, 3, 14, 0))
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

    private ServiceSnapshot existingService() {
        return existingService(null);
    }

    private ServiceSnapshot existingService(BigDecimal operationRadiusKm) {
        return new ServiceSnapshot(
                SERVICE_ID,
                OFFERER_ID,
                "Plomeria",
                null,
                List.of(),
                new BigDecimal("25.00"),
                null,
                null,
                true,
                operationRadiusKm,
                null,
                null,
                null);
    }

    private ServiceAvailabilitySnapshot availability(int weekDay, LocalTime start, LocalTime end) {
        return availability(weekDay, start, end, true);
    }

    private ServiceAvailabilitySnapshot availability(int weekDay, LocalTime start, LocalTime end, boolean active) {
        return new ServiceAvailabilitySnapshot(
                (long) weekDay,
                SERVICE_ID,
                weekDay,
                start,
                end,
                active);
    }

    private AddressSnapshot clientAddress() {
        return new AddressSnapshot(
                ADDRESS_ID, CLIENT_ID, null, null,
                new BigDecimal("-75.5700"), new BigDecimal("6.2442"), null);
    }

    private ProfileSnapshot clientProfile() {
        return profile(CLIENT_ID, "Juan Perez", ADDRESS_ID);
    }

    private ProfileSnapshot offererProfile() {
        return profile(OFFERER_ID, "Maria Garcia", 50L);
    }

    private ProfileSnapshot profile(Long userId, String fullName, Long primaryAddressId) {
        return new ProfileSnapshot(
                null, userId, fullName, null, null, null,
                primaryAddressId, null, null, null, null);
    }
}
