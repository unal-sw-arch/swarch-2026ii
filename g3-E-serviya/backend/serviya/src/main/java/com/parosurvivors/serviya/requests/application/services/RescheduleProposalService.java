package com.parosurvivors.serviya.requests.application.services;

import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.profiles.application.ports.output.AddressPersistencePort;
import com.parosurvivors.serviya.profiles.application.ports.output.UserProfilePersistencePort;
import com.parosurvivors.serviya.profiles.domain.Address;
import com.parosurvivors.serviya.profiles.domain.UserProfile;
import com.parosurvivors.serviya.requests.application.dto.command.CreateRescheduleProposalCommand;
import com.parosurvivors.serviya.requests.application.dto.item.RescheduleProposalItem;
import com.parosurvivors.serviya.requests.application.dto.query.SearchRescheduleProposalsQuery;
import com.parosurvivors.serviya.requests.application.dto.result.RescheduleProposalDetailResult;
import com.parosurvivors.serviya.requests.application.mappers.RescheduleProposalCommandMapper;
import com.parosurvivors.serviya.requests.application.ports.input.RescheduleProposalServicePort;
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
import com.parosurvivors.serviya.shared.events.domain.RequestStatusChangedEvent;
import com.parosurvivors.serviya.shared.events.domain.RescheduleProposalCreatedEvent;
import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.shared.exceptions.UnauthorizedException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de propuestas de reprogramación (módulo 4). Implementa las escrituras (crear/aceptar/
 * rechazar/cancelar) y la resolución centralizada de propuestas PENDING que invocan los
 * orquestadores de {@link ServiceRequestCommandService}. Los reads siguen como placeholder.
 * Ver documents/project-structure/estructura-servicios.docx.
 */
@Component
@RequiredArgsConstructor
public class RescheduleProposalService implements RescheduleProposalServicePort {

    private final RescheduleProposalPersistencePort rescheduleProposalPersistencePort;
    private final RescheduleProposalReadPort rescheduleProposalReadPort;
    private final ServiceRequestPersistencePort serviceRequestPersistencePort;
    private final ServiceRequestReadPort serviceRequestReadPort;
    private final RescheduleProposalCommandMapper commandMapper;
    private final ServicePersistencePort servicePersistencePort;
    private final CategoryPersistencePort categoryPersistencePort;
    private final UserProfilePersistencePort userProfilePersistencePort;
    private final AddressPersistencePort addressPersistencePort;
    private final NotificationServicePort notificationServicePort;
    private final DomainEventPublisherPort eventPublisher;

    @Override
    @Transactional
    public RescheduleProposal createProposal(CreateRescheduleProposalCommand command) {
        ServiceRequest request = loadRequest(command.requestId());
        requireOwnership(request.getOffererId(), command.offererId());
        if (!request.isAccepted()) {
            throw new InvalidStateException(
                    "Sólo se puede proponer una reprogramación sobre una solicitud aceptada; estado actual: "
                            + request.getStatus());
        }
        // Sólo puede haber una propuesta PENDING por solicitud: la anterior (si existe) se cancela.
        cancelPendingProposals(command.requestId());

        RescheduleProposal proposal = commandMapper.toDomain(command);
        proposal.setStatus(ProposalStatus.PENDING);
        proposal.setCreatedAt(LocalDateTime.now());
        // Denormalizados desde la solicitud (inmutables): habilitan los listados por parte sin join.
        proposal.setClientId(request.getClientId());
        proposal.setOffererId(request.getOffererId());

        // Handle race condition: if another concurrent request created a PENDING proposal
        // after we cancelled, the unique constraint will fail. Retry once with fresh read.
        try {
            return saveProposalAndNotify(proposal, request, command);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueConstraintViolation(e)) {
                cancelPendingProposals(command.requestId());
                return saveProposalAndNotify(proposal, request, command);
            }
            throw e;
        }
    }

    private RescheduleProposal saveProposalAndNotify(RescheduleProposal proposal, ServiceRequest request,
                                                     CreateRescheduleProposalCommand command) {
        RescheduleProposal saved = rescheduleProposalPersistencePort.save(proposal);
        // La participación del oferente en el flujo de reprogramación es proponer: cuenta la propuesta.
        eventPublisher.publish(new RescheduleProposalCreatedEvent(
                saved.getId(), request.getId(), request.getOffererId(), request.getClientId()));
        userProfilePersistencePort.findByUserId(command.offererId())
                .ifPresent(offerer -> notificationServicePort.notify(
                        request.getClientId(),
                        "reschedule_proposed",
                        "Propuesta de reprogramación",
                        offerer.getFullName() + " propuso reprogramar tu servicio para el "
                                + command.proposedDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                        "SERVICE_REQUEST",
                        command.requestId(),
                        null,
                        Map.of()));
        return saved;
    }

    private boolean isUniqueConstraintViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.contains("uq_reschedule_pending_per_request")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Override
    @Transactional
    public ServiceRequest acceptProposal(Long proposalId, Long clientId) {
        RescheduleProposal proposal = loadProposal(proposalId);
        ServiceRequest request = loadRequest(proposal.getRequestId());
        requireOwnership(request.getClientId(), clientId);

        RequestStatus previous = request.getStatus();
        proposal.accept();
        request.markRescheduled(clientId);
        ServiceRequest replacement = request.rescheduleTo(
                proposal.getProposedDate(), RequestStatus.ACCEPTED, clientId);

        rescheduleProposalPersistencePort.update(proposal);
        serviceRequestPersistencePort.update(request);
        ServiceRequest saved = serviceRequestPersistencePort.save(replacement);
        // El cliente reprograma al aceptar la propuesta: la solicitud original pasa a RESCHEDULED
        // (lo cuenta el cliente) y el reemplazo nace ACCEPTED (lo cuentan ambos, por simetría con el
        // flujo libre donde el reemplazo PENDING se acepta después).
        publishStatusChanged(request, previous);
        publishStatusChanged(saved, null);
        userProfilePersistencePort.findByUserId(clientId)
                .ifPresent(client -> notificationServicePort.notify(
                        request.getOffererId(),
                        "reschedule_accepted",
                        "Reprogramación aceptada",
                        client.getFullName() + " aceptó la propuesta de reprogramación.",
                        "SERVICE_REQUEST",
                        request.getId(),
                        null,
                        Map.of()));
        return saved;
    }

    @Override
    @Transactional
    public void rejectProposal(Long proposalId, Long clientId) {
        RescheduleProposal proposal = loadProposal(proposalId);
        ServiceRequest request = loadRequest(proposal.getRequestId());
        requireOwnership(request.getClientId(), clientId);

        proposal.reject();
        rescheduleProposalPersistencePort.update(proposal);
        userProfilePersistencePort.findByUserId(clientId)
                .ifPresent(client -> notificationServicePort.notify(
                        request.getOffererId(),
                        "reschedule_rejected",
                        "Reprogramación rechazada",
                        client.getFullName() + " rechazó la propuesta de reprogramación.",
                        "SERVICE_REQUEST",
                        request.getId(),
                        null,
                        Map.of()));
    }

    @Override
    @Transactional
    public void cancelProposal(Long proposalId, Long offererId) {
        RescheduleProposal proposal = loadProposal(proposalId);
        ServiceRequest request = loadRequest(proposal.getRequestId());
        requireOwnership(request.getOffererId(), offererId);

        proposal.cancel();
        rescheduleProposalPersistencePort.update(proposal);
        userProfilePersistencePort.findByUserId(offererId)
                .ifPresent(offerer -> notificationServicePort.notify(
                        request.getClientId(),
                        "reschedule_cancelled",
                        "Propuesta retirada",
                        offerer.getFullName() + " retiró la propuesta de reprogramación.",
                        "SERVICE_REQUEST",
                        request.getId(),
                        null,
                        Map.of()));
    }

    @Override
    @Transactional
    public int supersedePendingProposals(Long requestId) {
        return resolvePending(requestId, RescheduleProposal::markSuperseded);
    }

    @Override
    @Transactional
    public int cancelPendingProposals(Long requestId) {
        return resolvePending(requestId, RescheduleProposal::cancel);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RescheduleProposalItem> getProposalsForClient(SearchRescheduleProposalsQuery query, Pageable pageable) {
        return rescheduleProposalReadPort.searchReceivedByClient(query, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RescheduleProposalItem> getProposalsByOfferer(SearchRescheduleProposalsQuery query, Pageable pageable) {
        return rescheduleProposalReadPort.searchSentByOfferer(query, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public RescheduleProposalDetailResult getProposalDetail(Long proposalId, Long viewerId) {
        // Carga -> verifica participacion -> enriquece (composicion de puertos, como el detalle de request):
        // existencia -> 404; participacion (partes denormalizadas en la propuesta) -> 403.
        RescheduleProposal proposal = loadProposal(proposalId);
        requireParticipant(proposal.getClientId(), proposal.getOffererId(), viewerId);

        ServiceRequest request = loadRequest(proposal.getRequestId());
        Service service = servicePersistencePort.findById(request.getServiceId()).orElse(null);
        String categoryName = service == null ? null
                : categoryPersistencePort.findById(service.getCategoryId()).map(Category::getName).orElse(null);
        String addressLabel = request.getAddressId() == null ? null
                : addressPersistencePort.findById(request.getAddressId()).map(Address::getCity).orElse(null);
        // La contraparte es la otra parte relativa al que consulta.
        Long counterpartyUserId = viewerId.equals(proposal.getClientId())
                ? proposal.getOffererId() : proposal.getClientId();
        UserProfile counterparty = userProfilePersistencePort.findByUserId(counterpartyUserId).orElse(null);

        return new RescheduleProposalDetailResult(
                proposal.getId(), proposal.getStatus().name(), proposal.getReason(), proposal.getProposedDate(),
                proposal.getCreatedAt(), proposal.getRespondedAt(),
                request.getId(), request.getStatus().name(), request.getScheduledDate(), request.getRequestedPrice(),
                addressLabel, request.getPreviousRequestId(),
                request.getServiceId(),
                service == null ? null : service.getTitle(), categoryName,
                service == null ? null : service.getPriceHourly(),
                service == null ? null : service.getAverageDurationMinutes(),
                counterpartyUserId,
                counterparty == null ? null : counterparty.getFullName(),
                counterparty == null ? null : counterparty.getProfilePhotoUrl());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RescheduleProposal> getProposalsByRequest(Long requestId, Long viewerId) {
        // Solo las partes de la solicitud pueden ver sus propuestas: existencia -> 404, participacion -> 403.
        ServiceRequest request = loadRequest(requestId);
        requireParticipant(request.getClientId(), request.getOffererId(), viewerId);
        return rescheduleProposalReadPort.findByRequestId(requestId);
    }

    // =====================================================
    // HELPERS
    // =====================================================

    /** Aplica {@code transition} a cada propuesta PENDING de la solicitud y las persiste. */
    private int resolvePending(Long requestId, Consumer<RescheduleProposal> transition) {
        List<RescheduleProposal> pending = rescheduleProposalReadPort
                .findByRequestIdAndStatus(requestId, ProposalStatus.PENDING);
        for (RescheduleProposal proposal : pending) {
            transition.accept(proposal);
            rescheduleProposalPersistencePort.update(proposal);
        }
        return pending.size();
    }

    private RescheduleProposal loadProposal(Long proposalId) {
        return rescheduleProposalReadPort.findById(proposalId)
                .orElseThrow(() -> new ResourceNotFoundException("Propuesta no encontrada: " + proposalId));
    }

    private ServiceRequest loadRequest(Long requestId) {
        return serviceRequestReadPort.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada: " + requestId));
    }

    private void requireOwnership(Long ownerId, Long actorId) {
        if (ownerId == null || !ownerId.equals(actorId)) {
            throw new UnauthorizedException("El usuario no es el propietario del recurso");
        }
    }

    /** El actor debe ser una de las partes (cliente u oferente) de la solicitud/propuesta. */
    private void requireParticipant(Long clientId, Long offererId, Long actorId) {
        boolean isParticipant = actorId != null
                && (actorId.equals(clientId) || actorId.equals(offererId));
        if (!isParticipant) {
            throw new UnauthorizedException("El usuario no participa en la solicitud");
        }
    }

    /** Publica el cambio de estado de una solicitud para que las métricas se actualicen por evento. */
    private void publishStatusChanged(ServiceRequest request, RequestStatus previous) {
        eventPublisher.publish(new RequestStatusChangedEvent(
                request.getId(),
                request.getClientId(),
                request.getOffererId(),
                request.getServiceId(),
                previous,
                request.getStatus()));
    }
}
