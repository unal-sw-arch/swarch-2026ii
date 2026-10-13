package com.parosurvivors.serviya.requests.application.ports.input;

import com.parosurvivors.serviya.requests.application.dto.command.CreateServiceRequestCommand;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Puerto de entrada de ServiceRequestCommandService (comandos / transiciones de estado — CQRS).
 * createRequest recibe Command y devuelve dominio; las transiciones usan escalares (id + actorId del JWT).
 * Ver documents/project-structure/estructura-servicios.docx (módulo 4).
 */
public interface ServiceRequestCommandServicePort {

    ServiceRequest createRequest(CreateServiceRequestCommand command);

    boolean checkServiceAvailability(Long serviceId, LocalDateTime scheduledDate);

    boolean checkWithinRadius(Long serviceId, Long clientAddressId);

    void acceptRequest(Long requestId, Long offererId);

    void rejectRequest(Long requestId, Long offererId);

    void markAsPresumablyCompleted(Long requestId, Long offererId);

    void confirmCompletion(Long requestId, Long clientId);

    /**
     * Marca la solicitud como no prestada (disputa resuelta o vencimiento sin prestarse). No se expone
     * como endpoint: la invocan el orquestador de moderación (admin) y el mantenimiento programado (sistema);
     * el control de acceso es responsabilidad de esos llamadores, no de esta transición.
     */
    void markAsNotProvided(Long requestId, Long actorId);

    void cancelRequest(Long requestId, Long userId);

    /**
     * Cancela todas las solicitudes activas (PENDING/ACCEPTED) del usuario (como cliente u oferente).
     * No se expone como endpoint: la invoca el orquestador de eliminación de cuenta (UserDeletionService)
     * para no dejar solicitudes huérfanas. El control de acceso es responsabilidad del llamador.
     */
    List<ServiceRequest> cancelActiveRequestsForUser(Long userId);

    /**
     * RF-066: cancela las solicitudes activas del usuario SOLO en el rol indicado. Al removerle el rol
     * OFFERER se cancelan las solicitudes en las que actuaba como oferente, pero NO las que tiene como
     * cliente (ese rol lo conserva), y viceversa.
     *
     * @param asOfferer true = solicitudes donde es oferente; false = donde es cliente
     * @return las solicitudes canceladas, para notificar a cada contraparte
     */
    List<ServiceRequest> cancelActiveRequestsForRole(Long userId, boolean asOfferer);

    ServiceRequest rescheduleRequest(Long requestId, LocalDateTime newDate, Long clientId);
}
