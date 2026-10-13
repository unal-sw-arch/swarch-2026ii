package com.parosurvivors.serviya.requests.application.services;

import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.requests.application.ports.input.RequestMaintenanceServicePort;
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
import com.parosurvivors.serviya.shared.events.domain.RequestStatusChangedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tareas de mantenimiento por tiempo del módulo 4 (disparadas por {@code RequestMaintenanceScheduler}).
 * Cada tarea recorre las solicitudes/propuestas vencidas, aplica la transición del dominio (idempotente)
 * y publica {@link RequestStatusChangedEvent} para que las métricas se actualicen por evento, igual que
 * las transiciones interactivas de {@link ServiceRequestCommandService}. El actor de las transiciones del
 * sistema es {@code null} (no hay usuario real). Ver documents/project-structure/estructura-servicios.docx.
 */
@Component
@RequiredArgsConstructor
public class RequestMaintenanceService implements RequestMaintenanceServicePort {

    /** Actor de las transiciones ejecutadas por el sistema (no hay usuario que las origine). */
    private static final Long SYSTEM_ACTOR = null;

    private final ServiceRequestPersistencePort serviceRequestPersistencePort;
    private final ServiceRequestReadPort serviceRequestReadPort;
    private final ServiceRequestCommandServicePort serviceRequestCommandService;
    private final RescheduleProposalReadPort rescheduleProposalReadPort;
    private final RescheduleProposalPersistencePort rescheduleProposalPersistencePort;
    private final NotificationServicePort notificationServicePort;
    private final DomainEventPublisherPort eventPublisher;

    /** Horas tras la fecha agendada para dar por no prestada una solicitud aceptada sin novedad. */
    @Value("${serviya.maintenance.accepted-grace-hours:24}")
    private long acceptedGraceHours;

    /** Horas tras marcarse como prestada para auto-confirmar si el cliente no responde. */
    @Value("${serviya.maintenance.completion-grace-hours:72}")
    private long completionGraceHours;

    @Override
    @Transactional
    public void rejectExpiredPendingRequests() {
        // Pendiente cuya fecha ya pasó sin que el oferente respondiera: se rechaza.
        List<ServiceRequest> expired = serviceRequestReadPort
                .findByStatusAndScheduledDateBefore(RequestStatus.PENDING, LocalDateTime.now());
        for (ServiceRequest request : expired) {
            RequestStatus previous = request.getStatus();
            request.reject(SYSTEM_ACTOR);
            serviceRequestPersistencePort.update(request);
            publishStatusChanged(request, previous);
            notificationServicePort.notify(
                    request.getClientId(),
                    "request_expired",
                    "Solicitud vencida",
                    "Tu solicitud pendiente venció por falta de respuesta del oferente y fue rechazada.",
                    "SERVICE_REQUEST",
                    request.getId(),
                    null,
                    Map.of());
        }
    }

    @Override
    public void markStaleAcceptedAsNotProvided() {
        // Aceptada cuya fecha venció hace más de la gracia sin marcarse como prestada. Se delega en el
        // servicio de comandos como SISTEMA (actorId null) para seguir su flujo completo: si hay una
        // propuesta de reprogramación pendiente, no es incumplimiento y se cancela en vez de marcarse como
        // no prestada. Cada solicitud se procesa en su propia transacción (aislamiento por item).
        LocalDateTime cutoff = LocalDateTime.now().minusHours(acceptedGraceHours);
        List<ServiceRequest> stale = serviceRequestReadPort
                .findByStatusAndScheduledDateBefore(RequestStatus.ACCEPTED, cutoff);
        for (ServiceRequest request : stale) {
            serviceRequestCommandService.markAsNotProvided(request.getId(), SYSTEM_ACTOR);
        }
    }

    @Override
    @Transactional
    public void rejectExpiredProposals() {
        // Propuesta PENDING cuya fecha propuesta ya pasó: queda sin sentido, se rechaza.
        List<RescheduleProposal> expired = rescheduleProposalReadPort
                .findByStatusAndProposedDateBefore(ProposalStatus.PENDING, LocalDateTime.now());
        for (RescheduleProposal proposal : expired) {
            proposal.reject();
            rescheduleProposalPersistencePort.update(proposal);
            notificationServicePort.notify(
                    proposal.getOffererId(),
                    "proposal_expired",
                    "Propuesta vencida",
                    "Tu propuesta de reprogramación venció y fue rechazada automáticamente.",
                    "SERVICE_REQUEST",
                    proposal.getRequestId(),
                    null,
                    Map.of());
        }
    }

    @Override
    @Transactional
    public void finalizeUnconfirmedCompletions() {
        // Presuntamente prestada sin confirmación del cliente pasada la gracia: se auto-confirma como completada.
        LocalDateTime cutoff = LocalDateTime.now().minusHours(completionGraceHours);
        List<ServiceRequest> unconfirmed = serviceRequestReadPort
                .findByStatusAndCompletedAtBefore(RequestStatus.PRESUMABLY_COMPLETED, cutoff);
        for (ServiceRequest request : unconfirmed) {
            RequestStatus previous = request.getStatus();
            request.confirmCompletion(SYSTEM_ACTOR);
            serviceRequestPersistencePort.update(request);
            publishStatusChanged(request, previous);
            notificationServicePort.notify(
                    request.getClientId(),
                    "auto_completed",
                    "Servicio completado",
                    "El servicio se marcó como completado automáticamente por falta de respuesta.",
                    "SERVICE_REQUEST",
                    request.getId(),
                    null,
                    Map.of());
            notificationServicePort.notify(
                    request.getOffererId(),
                    "auto_completed",
                    "Servicio completado",
                    "El servicio se marcó como completado automáticamente por falta de respuesta del cliente.",
                    "SERVICE_REQUEST",
                    request.getId(),
                    null,
                    Map.of());
        }
    }

    /** Publica el cambio de estado para que las métricas de oferente/cliente se actualicen por evento. */
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
