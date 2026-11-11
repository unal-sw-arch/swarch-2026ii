package com.parosurvivors.serviya.requests.application.services;

import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.requests.application.dto.command.CreateServiceRequestCommand;
import com.parosurvivors.serviya.requests.application.ports.input.RescheduleProposalServicePort;
import com.parosurvivors.serviya.requests.application.mappers.ServiceRequestCommandMapper;
import com.parosurvivors.serviya.requests.application.ports.input.ServiceRequestCommandServicePort;
import com.parosurvivors.serviya.requests.application.ports.output.ServiceRequestPersistencePort;
import com.parosurvivors.serviya.requests.application.ports.output.ServiceRequestReadPort;
import com.parosurvivors.serviya.requests.domain.RequestStatus;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;
import com.parosurvivors.serviya.services.api.ServiceQueryApi;
import com.parosurvivors.serviya.services.api.dto.ServiceAvailabilitySnapshot;
import com.parosurvivors.serviya.services.api.dto.ServiceSnapshot;
import com.parosurvivors.serviya.shared.events.application.ChatEventPublisher;
import com.parosurvivors.serviya.shared.events.application.ports.output.DomainEventPublisherPort;
import com.parosurvivors.serviya.shared.events.domain.RequestCreatedEvent;
import com.parosurvivors.serviya.shared.events.domain.RequestStatusChangedEvent;
import com.parosurvivors.serviya.profiles.api.ProfileQueryApi;
import com.parosurvivors.serviya.profiles.api.dto.AddressSnapshot;
import com.parosurvivors.serviya.shared.exceptions.BusinessRuleException;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.shared.exceptions.ForbiddenException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de comandos / transiciones de estado de solicitudes (CQRS, módulo 4).
 * Cada transición valida el estado en el dominio, persiste y publica un {@link RequestStatusChangedEvent}
 * para que las métricas de oferente/cliente actualicen sus contadores (vía @TransactionalEventListener,
 * AFTER_COMMIT). La resolución de propuestas se delega en {@link RescheduleProposalServicePort}.
 * Ver documents/project-structure/estructura-servicios.docx.
 */
@Component
@RequiredArgsConstructor
public class ServiceRequestCommandService implements ServiceRequestCommandServicePort {

    private final ServiceRequestPersistencePort serviceRequestPersistencePort;
    private final ServiceRequestReadPort serviceRequestReadPort;
    private final RescheduleProposalServicePort rescheduleProposalService;
    private final NotificationServicePort notificationServicePort;
    private final ServiceRequestCommandMapper commandMapper;
    private final ServiceQueryApi serviceQueryApi;
    private final ProfileQueryApi profileQueryApi;
    private final DomainEventPublisherPort eventPublisher;
    private final ChatEventPublisher chatEventPublisher;

    @Override
    @Transactional
    public ServiceRequest createRequest(CreateServiceRequestCommand command) {
        ServiceSnapshot service = serviceQueryApi.findById(command.serviceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Servicio no encontrado con id: " + command.serviceId()));

        checkServiceAvailability(command.serviceId(), command.scheduledDate());
        checkWithinRadius(command.serviceId(), command.addressId());

        ServiceRequest serviceRequest = commandMapper.toDomain(command);
        serviceRequest.setOffererId(service.offererId());
        serviceRequest.setStatus(RequestStatus.PENDING);
        serviceRequest.setRequestedPrice(service.priceHourly());
        LocalDateTime now = LocalDateTime.now();
        serviceRequest.setCreatedAt(now);
        serviceRequest.setUpdatedStatusAt(now);
        ServiceRequest saved = serviceRequestPersistencePort.save(serviceRequest);
        // Solicitud original: alimenta requests_sent (cliente) y requests_received (oferente).
        eventPublisher.publish(new RequestCreatedEvent(
                saved.getId(), saved.getClientId(), saved.getOffererId(), saved.getServiceId()));
        // Evento de integracion para el chat: abre la conversacion REQUEST.
        chatEventPublisher.requestCreated(
                saved.getId(), saved.getClientId(), saved.getOffererId(), saved.getServiceId(),
                saved.getStatus() == null ? null : saved.getStatus().name(), saved.getCreatedAt());
        profileQueryApi.findProfile(command.clientId())
                .ifPresent(client -> notificationServicePort.notify(
                        service.offererId(),
                        "new_request",
                        "Nueva solicitud de servicio",
                        "Has recibido una solicitud de " + client.fullName()
                                + " para el servicio \"" + service.title() + "\"",
                        "SERVICE_REQUEST",
                        saved.getId(),
                        null,
                        Map.of()));
        return saved;
    }

    @Override
    public boolean checkServiceAvailability(Long serviceId, LocalDateTime scheduledDate) {
        List<ServiceAvailabilitySnapshot> availabilities = serviceQueryApi
                .getAvailabilities(serviceId);

        if (availabilities.isEmpty()) {
            return true;
        }

        int javaDayOfWeek = scheduledDate.getDayOfWeek().getValue();
        int dayIndex = javaDayOfWeek == 7 ? 0 : javaDayOfWeek;
        LocalTime scheduledTime = scheduledDate.toLocalTime();
        boolean isAvailable = false;

        for (ServiceAvailabilitySnapshot availability : availabilities) {
            if (availability.active()
                && availability.weekDay() == dayIndex
                && !scheduledTime.isBefore(availability.startTime())
                && !scheduledTime.isAfter(availability.endTime())
            ) {
                isAvailable = true;
                break;
            }
        }
        if (!isAvailable) {
            throw new BusinessRuleException(
                    "El servicio no está disponible en la fecha y hora solicitadas "
                );
        }
        return true;
    }

    @Override
    public boolean checkWithinRadius(Long serviceId, Long clientAddressId) {
        ServiceSnapshot service = serviceQueryApi.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Servicio no encontrado con id: " + serviceId));

        if (service.operationRadiusKm() == null
                || service.operationRadiusKm().compareTo(java.math.BigDecimal.ZERO) == 0) {
            return true;
        }

        AddressSnapshot clientAddress = profileQueryApi.findAddress(clientAddressId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dirección del cliente no encontrada: " + clientAddressId));

        AddressSnapshot offererAddress = profileQueryApi
                .findProfile(service.offererId())
                .flatMap(profile -> {
                    if (profile.primaryAddressId() == null) return java.util.Optional.empty();
                    return profileQueryApi.findAddress(profile.primaryAddressId());
                })
                .orElseThrow(() -> new BusinessRuleException(
                        "El oferente no tiene una dirección principal configurada"));

        double distance = distanceKmBetween(clientAddress, offererAddress);

        if (distance > service.operationRadiusKm().doubleValue()) {
            throw new BusinessRuleException(
                    "La dirección del cliente está fuera del radio de operación del servicio "
                            + "(" + String.format("%.2f", distance) + " km vs "
                            + service.operationRadiusKm() + " km permitidos)");
        }
        return true;
    }

    @Override
    @Transactional
    public void acceptRequest(Long requestId, Long offererId) {
        ServiceRequest request = loadRequest(requestId);
        requireOwnership(request.getOffererId(), offererId);
        RequestStatus previous = request.getStatus();
        request.accept(offererId);
        serviceRequestPersistencePort.update(request);
        publishStatusChanged(request, previous);
        profileQueryApi.findProfile(offererId)
                .ifPresent(offerer -> notificationServicePort.notify(
                        request.getClientId(),
                        "request_accepted",
                        "Solicitud aceptada",
                        "Tu solicitud de servicio fue aceptada por " + offerer.fullName(),
                        "SERVICE_REQUEST",
                        requestId,
                        null,
                        Map.of()));
    }

    @Override
    @Transactional
    public void rejectRequest(Long requestId, Long offererId) {
        ServiceRequest request = loadRequest(requestId);
        requireOwnership(request.getOffererId(), offererId);
        RequestStatus previous = request.getStatus();
        request.reject(offererId);
        serviceRequestPersistencePort.update(request);
        publishStatusChanged(request, previous);
        profileQueryApi.findProfile(offererId)
                .ifPresent(offerer -> notificationServicePort.notify(
                        request.getClientId(),
                        "request_rejected",
                        "Solicitud rechazada",
                        "Tu solicitud de servicio fue rechazada por " + offerer.fullName(),
                        "SERVICE_REQUEST",
                        requestId,
                        null,
                        Map.of()));
    }

    @Override
    @Transactional
    public void markAsPresumablyCompleted(Long requestId, Long offererId) {
        ServiceRequest request = loadRequest(requestId);
        requireOwnership(request.getOffererId(), offererId);
        RequestStatus previous = request.getStatus();
        // El oferente declara el servicio prestado: cualquier propuesta PENDING queda sin sentido.
        rescheduleProposalService.cancelPendingProposals(requestId);
        request.markAsPresumablyCompleted(offererId);
        serviceRequestPersistencePort.update(request);
        // PRESUMABLY_COMPLETED no tiene contador de métricas (los listeners lo ignoran); se publica
        // uniformemente para trazabilidad y para habilitar el feedback de ambas partes.
        publishStatusChanged(request, previous);
        profileQueryApi.findProfile(offererId)
                .ifPresent(offerer -> notificationServicePort.notify(
                        request.getClientId(),
                        "service_completed",
                        "Servicio realizado",
                        offerer.fullName() + " declaró el servicio como realizado. Confirma si fue así para calificar.",
                        "SERVICE_REQUEST",
                        requestId,
                        null,
                        Map.of()));
    }

    @Override
    @Transactional
    public void confirmCompletion(Long requestId, Long clientId) {
        ServiceRequest request = loadRequest(requestId);
        requireOwnership(request.getClientId(), clientId);
        RequestStatus previous = request.getStatus();
        request.confirmCompletion(clientId);
        serviceRequestPersistencePort.update(request);
        publishStatusChanged(request, previous);
        profileQueryApi.findProfile(clientId)
                .ifPresent(client -> notificationServicePort.notify(
                        request.getOffererId(),
                        "completion_confirmed",
                        "Servicio confirmado",
                        client.fullName() + " confirmó que el servicio fue completado.",
                        "SERVICE_REQUEST",
                        requestId,
                        null,
                        Map.of()));
    }

    @Override
    @Transactional
    public void markAsNotProvided(Long requestId, Long actorId) {
        // Sin validación de acceso aquí: la transición no se expone como endpoint; el control de acceso
        // lo hacen sus llamadores (el orquestador de moderación admin y el mantenimiento como sistema).
        ServiceRequest request = loadRequest(requestId);
        RequestStatus previous = request.getStatus();
        int cancelled = rescheduleProposalService.cancelPendingProposals(requestId);
        if (cancelled > 0) {
            // Había un aviso de reprogramación pendiente: no es incumplimiento, se cancela.
            request.cancel(actorId);
        } else {
            request.markAsNotProvided(actorId);
        }
        serviceRequestPersistencePort.update(request);
        // El estado final es CANCELLED o NOT_PROVIDED según la rama; el evento lleva el real.
        publishStatusChanged(request, previous);
        String outcome = request.getStatus() == RequestStatus.NOT_PROVIDED
                ? "no fue prestado"
                : "fue cancelado";
        notificationServicePort.notify(
                request.getClientId(),
                "service_not_provided",
                "Servicio no realizado",
                "El servicio solicitado " + outcome + ".",
                "SERVICE_REQUEST",
                requestId,
                null,
                Map.of());
        notificationServicePort.notify(
                request.getOffererId(),
                "service_not_provided",
                "Servicio no realizado",
                "El servicio programado " + outcome + ".",
                "SERVICE_REQUEST",
                requestId,
                null,
                Map.of());
    }

    @Override
    @Transactional
    public void cancelRequest(Long requestId, Long userId) {
        ServiceRequest request = loadRequest(requestId);
        requireParticipant(request, userId);
        RequestStatus previous = request.getStatus();
        request.cancel(userId);
        rescheduleProposalService.cancelPendingProposals(requestId);
        serviceRequestPersistencePort.update(request);
        publishStatusChanged(request, previous);
        boolean cancelledByClient = request.getClientId().equals(userId);
        Long counterpartyId = cancelledByClient ? request.getOffererId() : request.getClientId();
        String actorRole = cancelledByClient ? "el Cliente" : "el Oferente";
        notificationServicePort.notify(
                counterpartyId,
                "request_cancelled",
                "Solicitud cancelada",
                "La solicitud fue cancelada por " + actorRole + ".",
                "SERVICE_REQUEST",
                requestId,
                null,
                Map.of());
    }

    @Override
    @Transactional
    public List<ServiceRequest> cancelActiveRequestsForUser(Long userId) {
        // Cancela las solicitudes activas (PENDING/ACCEPTED) en las que el usuario participa como cliente
        // u oferente. Lo invoca UserDeletionService al eliminar la cuenta para no dejar solicitudes huérfanas.
        // Se filtra en BD por participante + estado (no se traen las ya finalizadas); el usuario es participante
        // de todas ellas, así que cancelRequest(id, userId) supera el check de propiedad.
        List<ServiceRequest> active = serviceRequestReadPort.findByParticipantAndStatusIn(
                userId, List.of(RequestStatus.PENDING, RequestStatus.ACCEPTED));
        for (ServiceRequest request : active) {
            cancelRequest(request.getId(), userId);
        }
        // Se devuelven las canceladas para que el llamador (RF-008) pueda avisar a cada contraparte.
        return active;
    }

    /**
     * RF-066: variante acotada a UN rol. Al remover el rol OFFERER solo se cancelan las solicitudes en
     * las que el usuario es el oferente; las que tiene como cliente siguen vivas porque conserva ese rol.
     */
    @Override
    @Transactional
    public List<ServiceRequest> cancelActiveRequestsForRole(Long userId, boolean asOfferer) {
        List<ServiceRequest> scoped = serviceRequestReadPort
                .findByParticipantAndStatusIn(userId, List.of(RequestStatus.PENDING, RequestStatus.ACCEPTED))
                .stream()
                .filter(request -> asOfferer
                        ? userId.equals(request.getOffererId())
                        : userId.equals(request.getClientId()))
                .toList();
        for (ServiceRequest request : scoped) {
            cancelRequest(request.getId(), userId);
        }
        return scoped;
    }

    @Override
    @Transactional
    public ServiceRequest rescheduleRequest(Long requestId, LocalDateTime newDate, Long clientId) {
        ServiceRequest request = loadRequest(requestId);
        requireOwnership(request.getClientId(), clientId);

        RequestStatus previous = request.getStatus();
        request.markRescheduled(clientId);
        ServiceRequest replacement = request.rescheduleTo(newDate, RequestStatus.PENDING, clientId);
        // Reprogramación libre: supera cualquier propuesta PENDING del oferente sobre esta solicitud.
        rescheduleProposalService.supersedePendingProposals(requestId);

        serviceRequestPersistencePort.update(request);
        ServiceRequest saved = serviceRequestPersistencePort.save(replacement);
        // Solo la solicitud original cambia de estado (RESCHEDULED); el reemplazo nace PENDING (sin contador).
        publishStatusChanged(request, previous);
        profileQueryApi.findProfile(clientId)
                .ifPresent(client -> notificationServicePort.notify(
                        request.getOffererId(),
                        "request_rescheduled",
                        "Servicio reprogramado",
                        client.fullName() + " reprogramó el servicio para el "
                                + newDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                        "SERVICE_REQUEST",
                        requestId,
                        null,
                        Map.of()));
        return saved;
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private ServiceRequest loadRequest(Long requestId) {
        return serviceRequestReadPort.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada: " + requestId));
    }

    /**
     * Publica el cambio de estado para que las métricas de oferente/cliente se actualicen por evento
     * (AFTER_COMMIT). El evento es autocontenido: lleva los ids desnormalizados y el estado nuevo.
     */
    private void publishStatusChanged(ServiceRequest request, RequestStatus previous) {
        eventPublisher.publish(new RequestStatusChangedEvent(
                request.getId(),
                request.getClientId(),
                request.getOffererId(),
                request.getServiceId(),
                previous != null ? previous.name() : null,
                request.getStatus() != null ? request.getStatus().name() : null));
        chatEventPublisher.requestStatusChanged(
                request.getId(),
                request.getUpdatedBy(),
                previous != null ? previous.name() : null,
                request.getStatus() != null ? request.getStatus().name() : null,
                request.getUpdatedStatusAt());
    }

    private void requireOwnership(Long ownerId, Long actorId) {
        if (ownerId == null || !ownerId.equals(actorId)) {
            throw new ForbiddenException("El usuario no es el propietario del recurso");
        }
    }

    /** El actor debe ser el cliente o el oferente de la solicitud. */
    private void requireParticipant(ServiceRequest request, Long actorId) {
        boolean isParticipant = actorId != null
                && (actorId.equals(request.getClientId()) || actorId.equals(request.getOffererId()));
        if (!isParticipant) {
            throw new ForbiddenException("El usuario no participa en la solicitud");
        }
    }

    /** Haversine entre dos direcciones; misma formula que usaba {@code Address.distanceKmTo}. */
    private static double distanceKmBetween(AddressSnapshot from, AddressSnapshot to) {
        if (from.latitude() == null || from.longitude() == null
                || to.latitude() == null || to.longitude() == null) {
            throw new IllegalStateException("Faltan coordenadas para calcular la distancia");
        }
        final double earthRadiusKm = 6371.0;
        double lat1 = Math.toRadians(from.latitude().doubleValue());
        double lat2 = Math.toRadians(to.latitude().doubleValue());
        double deltaLat = Math.toRadians(to.latitude().subtract(from.latitude()).doubleValue());
        double deltaLon = Math.toRadians(to.longitude().subtract(from.longitude()).doubleValue());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}
