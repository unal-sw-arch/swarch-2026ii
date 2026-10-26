package com.parosurvivors.serviya.requests.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.profiles.application.ports.output.AddressPersistencePort;
import com.parosurvivors.serviya.profiles.application.ports.output.UserProfilePersistencePort;
import com.parosurvivors.serviya.profiles.domain.Address;
import com.parosurvivors.serviya.profiles.domain.UserProfile;
import com.parosurvivors.serviya.requests.application.dto.command.CreateRescheduleProposalCommand;
import com.parosurvivors.serviya.requests.application.mappers.RescheduleProposalCommandMapper;
import com.parosurvivors.serviya.requests.application.ports.output.RescheduleProposalPersistencePort;
import com.parosurvivors.serviya.requests.application.ports.output.RescheduleProposalReadPort;
import com.parosurvivors.serviya.requests.application.ports.output.ServiceRequestPersistencePort;
import com.parosurvivors.serviya.requests.application.ports.output.ServiceRequestReadPort;
import com.parosurvivors.serviya.requests.domain.ProposalStatus;
import com.parosurvivors.serviya.requests.domain.RequestStatus;
import com.parosurvivors.serviya.requests.domain.RescheduleProposal;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;
import com.parosurvivors.serviya.services.application.ports.output.CategoryPersistencePort;
import com.parosurvivors.serviya.services.application.ports.output.ServicePersistencePort;
import com.parosurvivors.serviya.services.domain.Category;
import com.parosurvivors.serviya.services.domain.Service;
import com.parosurvivors.serviya.shared.events.application.ports.output.DomainEventPublisherPort;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RescheduleProposalServiceTest {

    @Mock RescheduleProposalPersistencePort rescheduleProposalPersistencePort;
    @Mock RescheduleProposalReadPort rescheduleProposalReadPort;
    @Mock ServiceRequestPersistencePort serviceRequestPersistencePort;
    @Mock ServiceRequestReadPort serviceRequestReadPort;
    @Mock RescheduleProposalCommandMapper commandMapper;
    @Mock ServicePersistencePort servicePersistencePort;
    @Mock CategoryPersistencePort categoryPersistencePort;
    @Mock UserProfilePersistencePort userProfilePersistencePort;
    @Mock AddressPersistencePort addressPersistencePort;
    @Mock NotificationServicePort notificationServicePort;
    @Mock DomainEventPublisherPort eventPublisher;

    @InjectMocks RescheduleProposalService service;

    private static final Long PROPOSAL_ID = 100L;
    private static final Long REQUEST_ID = 1L;
    private static final Long CLIENT_ID = 10L;
    private static final Long OFFERER_ID = 20L;

    // =====================================================
    // createProposal
    // =====================================================

    @Test
    void createProposal_succeeds_whenRequestAccepted() {
        CreateRescheduleProposalCommand command = createProposalCommand();
        ServiceRequest request = acceptedRequest();
        RescheduleProposal unmapped = RescheduleProposal.builder().reason("Conflicto").build();
        RescheduleProposal saved = pendingProposal();

        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(rescheduleProposalReadPort.findByRequestIdAndStatus(REQUEST_ID, ProposalStatus.PENDING))
                .thenReturn(Collections.emptyList());
        when(commandMapper.toDomain(command)).thenReturn(unmapped);
        when(rescheduleProposalPersistencePort.save(any())).thenReturn(saved);
        when(userProfilePersistencePort.findByUserId(OFFERER_ID)).thenReturn(Optional.of(offererProfile()));

        RescheduleProposal result = service.createProposal(command);

        assertThat(result).isSameAs(saved);
        verify(eventPublisher).publish(any());
        verify(notificationServicePort).notify(
                eq(CLIENT_ID), eq("reschedule_proposed"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createProposal_throws_whenRequestNotAccepted() {
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest()));

        assertThatThrownBy(() -> service.createProposal(createProposalCommand()))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("aceptada");
    }

    @Test
    void createProposal_throws_whenNotOfferer() {
        ServiceRequest request = acceptedRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        CreateRescheduleProposalCommand command = new CreateRescheduleProposalCommand(
                REQUEST_ID, CLIENT_ID, "Conflicto", LocalDateTime.of(2026, 8, 15, 10, 0));

        assertThatThrownBy(() -> service.createProposal(command))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void createProposal_throws_whenRequestNotFound() {
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createProposal(createProposalCommand()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createProposal_supersedesExistingPendingProposal() {
        CreateRescheduleProposalCommand command = createProposalCommand();
        ServiceRequest request = acceptedRequest();
        RescheduleProposal existing = pendingProposal();
        RescheduleProposal saved = pendingProposal();

        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(rescheduleProposalReadPort.findByRequestIdAndStatus(REQUEST_ID, ProposalStatus.PENDING))
                .thenReturn(List.of(existing));
        when(commandMapper.toDomain(command)).thenReturn(RescheduleProposal.builder().reason("x").build());
        when(rescheduleProposalPersistencePort.save(any())).thenReturn(saved);

        service.createProposal(command);

        assertThat(existing.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
        verify(rescheduleProposalPersistencePort).update(existing);
    }

    // =====================================================
    // acceptProposal
    // =====================================================

    @Test
    void acceptProposal_createsReplacement() {
        RescheduleProposal proposal = pendingProposal();
        proposal.setId(PROPOSAL_ID);
        ServiceRequest request = acceptedRequest();
        request.setId(REQUEST_ID);
        ServiceRequest replacement = ServiceRequest.builder()
                .id(2L).previousRequestId(REQUEST_ID).status(RequestStatus.ACCEPTED)
                .clientId(CLIENT_ID).offererId(OFFERER_ID).build();

        when(rescheduleProposalReadPort.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(serviceRequestPersistencePort.save(any())).thenReturn(replacement);
        when(userProfilePersistencePort.findByUserId(CLIENT_ID)).thenReturn(Optional.of(clientProfile()));

        ServiceRequest result = service.acceptProposal(PROPOSAL_ID, CLIENT_ID);

        assertThat(result).isSameAs(replacement);
        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.RESCHEDULED);
        verify(serviceRequestPersistencePort).update(request);
        verify(serviceRequestPersistencePort).save(any());
        verify(eventPublisher, org.mockito.Mockito.times(2)).publish(any());
    }

    @Test
    void acceptProposal_throws_whenNotClient() {
        RescheduleProposal proposal = pendingProposal();
        ServiceRequest request = acceptedRequest();
        when(rescheduleProposalReadPort.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.acceptProposal(PROPOSAL_ID, OFFERER_ID))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void acceptProposal_throws_whenProposalNotFound() {
        when(rescheduleProposalReadPort.findById(PROPOSAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptProposal(PROPOSAL_ID, CLIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =====================================================
    // rejectProposal
    // =====================================================

    @Test
    void rejectProposal_setsRejected() {
        RescheduleProposal proposal = pendingProposal();
        ServiceRequest request = acceptedRequest();
        when(rescheduleProposalReadPort.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(userProfilePersistencePort.findByUserId(CLIENT_ID)).thenReturn(Optional.of(clientProfile()));

        service.rejectProposal(PROPOSAL_ID, CLIENT_ID);

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        verify(rescheduleProposalPersistencePort).update(proposal);
        verify(notificationServicePort).notify(
                eq(OFFERER_ID), eq("reschedule_rejected"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectProposal_throws_whenNotClient() {
        RescheduleProposal proposal = pendingProposal();
        ServiceRequest request = acceptedRequest();
        when(rescheduleProposalReadPort.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.rejectProposal(PROPOSAL_ID, OFFERER_ID))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =====================================================
    // cancelProposal
    // =====================================================

    @Test
    void cancelProposal_setsCancelled() {
        RescheduleProposal proposal = pendingProposal();
        ServiceRequest request = acceptedRequest();
        when(rescheduleProposalReadPort.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(userProfilePersistencePort.findByUserId(OFFERER_ID)).thenReturn(Optional.of(offererProfile()));

        service.cancelProposal(PROPOSAL_ID, OFFERER_ID);

        assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
        verify(rescheduleProposalPersistencePort).update(proposal);
        verify(notificationServicePort).notify(
                eq(CLIENT_ID), eq("reschedule_cancelled"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void cancelProposal_throws_whenNotOfferer() {
        RescheduleProposal proposal = pendingProposal();
        ServiceRequest request = acceptedRequest();
        when(rescheduleProposalReadPort.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancelProposal(PROPOSAL_ID, CLIENT_ID))
                .isInstanceOf(UnauthorizedException.class);
    }

    // =====================================================
    // supersedePendingProposals / cancelPendingProposals
    // =====================================================

    @Test
    void supersedePendingProposals_transitionsAllPending() {
        RescheduleProposal p1 = pendingProposal();
        p1.setId(1L);
        RescheduleProposal p2 = pendingProposal();
        p2.setId(2L);

        when(rescheduleProposalReadPort.findByRequestIdAndStatus(REQUEST_ID, ProposalStatus.PENDING))
                .thenReturn(List.of(p1, p2));

        int count = service.supersedePendingProposals(REQUEST_ID);

        assertThat(count).isEqualTo(2);
        assertThat(p1.getStatus()).isEqualTo(ProposalStatus.SUPERSEDED);
        assertThat(p2.getStatus()).isEqualTo(ProposalStatus.SUPERSEDED);
        verify(rescheduleProposalPersistencePort).update(p1);
        verify(rescheduleProposalPersistencePort).update(p2);
    }

    @Test
    void cancelPendingProposals_transitionsAllPendingToCancelled() {
        RescheduleProposal p1 = pendingProposal();
        when(rescheduleProposalReadPort.findByRequestIdAndStatus(REQUEST_ID, ProposalStatus.PENDING))
                .thenReturn(List.of(p1));

        int count = service.cancelPendingProposals(REQUEST_ID);

        assertThat(count).isEqualTo(1);
        assertThat(p1.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
    }

    @Test
    void resolvePending_returnsZero_whenNoPendingProposals() {
        when(rescheduleProposalReadPort.findByRequestIdAndStatus(REQUEST_ID, ProposalStatus.PENDING))
                .thenReturn(Collections.emptyList());

        int count = service.cancelPendingProposals(REQUEST_ID);

        assertThat(count).isEqualTo(0);
        verify(rescheduleProposalPersistencePort, never()).update(any());
    }

    // =====================================================
    // getProposalsByRequest
    // =====================================================

    @Test
    void getProposalsByRequest_returnsProposals_whenParticipant() {
        ServiceRequest request = pendingRequest();
        RescheduleProposal proposal = pendingProposal();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(rescheduleProposalReadPort.findByRequestId(REQUEST_ID)).thenReturn(List.of(proposal));

        List<RescheduleProposal> result = service.getProposalsByRequest(REQUEST_ID, CLIENT_ID);

        assertThat(result).hasSize(1);
    }

    @Test
    void getProposalsByRequest_throws_whenNotParticipant() {
        ServiceRequest request = pendingRequest();
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.getProposalsByRequest(REQUEST_ID, 999L))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getProposalsByRequest_throws_whenRequestNotFound() {
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProposalsByRequest(REQUEST_ID, CLIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =====================================================
    // getProposalDetail
    // =====================================================

    @Test
    void getProposalDetail_returnsDetail_whenParticipant() {
        RescheduleProposal proposal = pendingProposal();
        proposal.setId(PROPOSAL_ID);
        ServiceRequest request = acceptedRequest();

        when(rescheduleProposalReadPort.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));
        when(serviceRequestReadPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(servicePersistencePort.findById(REQUEST_ID)).thenReturn(Optional.of(domainService()));
        when(categoryPersistencePort.findById(1L)).thenReturn(Optional.of(category()));
        when(userProfilePersistencePort.findByUserId(OFFERER_ID)).thenReturn(Optional.of(offererProfile()));
        when(addressPersistencePort.findById(40L)).thenReturn(Optional.of(address()));

        var result = service.getProposalDetail(PROPOSAL_ID, CLIENT_ID);

        assertThat(result.proposalId()).isEqualTo(PROPOSAL_ID);
        assertThat(result.counterpartyUserId()).isEqualTo(OFFERER_ID);
        assertThat(result.counterpartyName()).isEqualTo("Maria Garcia");
    }

    @Test
    void getProposalDetail_throws_whenNotParticipant() {
        RescheduleProposal proposal = pendingProposal();
        when(rescheduleProposalReadPort.findById(PROPOSAL_ID)).thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> service.getProposalDetail(PROPOSAL_ID, 999L))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getProposalDetail_throws_whenProposalNotFound() {
        when(rescheduleProposalReadPort.findById(PROPOSAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProposalDetail(PROPOSAL_ID, CLIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private CreateRescheduleProposalCommand createProposalCommand() {
        return new CreateRescheduleProposalCommand(
                REQUEST_ID, OFFERER_ID, "Conflicto de horario",
                LocalDateTime.of(2026, 8, 15, 10, 0));
    }

    private ServiceRequest pendingRequest() {
        return ServiceRequest.builder()
                .id(REQUEST_ID)
                .serviceId(1L)
                .clientId(CLIENT_ID)
                .offererId(OFFERER_ID)
                .addressId(40L)
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

    private RescheduleProposal pendingProposal() {
        return RescheduleProposal.builder()
                .id(PROPOSAL_ID)
                .requestId(REQUEST_ID)
                .clientId(CLIENT_ID)
                .offererId(OFFERER_ID)
                .reason("Conflicto de horario")
                .proposedDate(LocalDateTime.of(2026, 8, 15, 10, 0))
                .status(ProposalStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Service domainService() {
        return Service.builder()
                .id(1L)
                .title("Plomeria")
                .categoryId(1L)
                .priceHourly(new BigDecimal("25.00"))
                .build();
    }

    private Category category() {
        return Category.builder().id(1L).name("Hogar").build();
    }

    private UserProfile clientProfile() {
        return UserProfile.builder()
                .userId(CLIENT_ID)
                .fullName("Juan Perez")
                .build();
    }

    private UserProfile offererProfile() {
        return UserProfile.builder()
                .userId(OFFERER_ID)
                .fullName("Maria Garcia")
                .profilePhotoUrl("photo.jpg")
                .build();
    }

    private Address address() {
        return Address.builder()
                .id(40L)
                .city("Medellin")
                .build();
    }
}
