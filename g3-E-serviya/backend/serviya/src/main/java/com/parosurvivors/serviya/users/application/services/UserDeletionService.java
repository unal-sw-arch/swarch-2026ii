package com.parosurvivors.serviya.users.application.services;

import com.parosurvivors.serviya.notifications.api.Channel;
import com.parosurvivors.serviya.notifications.api.NotificationCommand;
import com.parosurvivors.serviya.notifications.api.NotificationCommandApi;
import com.parosurvivors.serviya.requests.api.RequestCommandApi;
import com.parosurvivors.serviya.requests.api.dto.RequestSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.parosurvivors.serviya.services.api.ServiceCommandApi;
import com.parosurvivors.serviya.shared.events.application.ChatEventPublisher;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.users.application.ports.input.SessionRevocationServicePort;
import com.parosurvivors.serviya.users.application.ports.input.UserDeletionServicePort;
import com.parosurvivors.serviya.users.application.ports.output.UserPersistencePort;
import com.parosurvivors.serviya.users.application.ports.output.UserReadPort;
import com.parosurvivors.serviya.users.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestador de la eliminación (soft delete) de un usuario que puede ser oferente y/o cliente
 * con solicitudes activas. Evita solicitudes huérfanas: desactiva los servicios del oferente y
 * cancela las solicitudes activas donde participa. Todo atómico (@Transactional).
 * Ver documents/project-structure/estructura-servicios.docx (módulo 1).
 */
@Component
@RequiredArgsConstructor
public class UserDeletionService implements UserDeletionServicePort {

    private final UserPersistencePort userPersistencePort;
    private final UserReadPort userReadPort;
    private final ServiceCommandApi serviceCommandApi;
    private final RequestCommandApi requestCommandApi;
    private final NotificationCommandApi notificationCommandApi;
    /** Cierre total de sesión (familias de refresh + min_iat) al eliminar la cuenta (P2.5). */
    private final SessionRevocationServicePort sessionRevocationService;
    private final com.parosurvivors.serviya.shared.audit.SecurityAuditServicePort auditService;
    private final ChatEventPublisher chatEventPublisher;

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = userReadPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        if (user.isDeleted()) {
            return; // idempotente: la cuenta ya está eliminada.
        }
        // (1) Soft delete de la cuenta.
        user.softDelete();
        userPersistencePort.update(user);
        // Eliminar la cuenta cierra la sesión en todos los dispositivos (P2.5): revoca las familias de
        // refresh y escribe min_iat. Dentro de la misma transacción que el soft delete.
        sessionRevocationService.revokeAllSessions(userId);
        auditService.record(com.parosurvivors.serviya.shared.audit.AuditEventType.ACCOUNT_DELETE, userId, true, null);
        // Evento de integración: el chat marca al usuario como eliminado.
        chatEventPublisher.userDeleted(userId);
        // (2) Si es oferente, ocultar todos sus servicios del buscador (no-op si no tiene servicios).
        serviceCommandApi.deactivateAllByOfferer(userId);
        // (3) Cancelar las solicitudes activas donde participa (como cliente u oferente) para no dejar huérfanas.
        //     cancelRequest ya publica RequestStatusChangedEvent (métricas) y avisa IN_APP a la contraparte.
        List<RequestSnapshot> cancelled = requestCommandApi.cancelActiveRequestsForUser(userId);
        // (4) Notificación dual-canal: el aviso IN_APP ("Solicitud cancelada") ya lo emite cancelRequest;
        //     aquí se completa el otro canal (EMAIL) explicando el motivo real —la cuenta fue eliminada—,
        //     que el aviso genérico de cancelación no transmite. Así la contraparte queda informada por
        //     ambos canales sin recibir dos veces la misma notificación interna.
        notifyCounterpartiesByEmail(userId, cancelled);
    }

    /** Avisa por EMAIL a la contraparte de cada solicitud cancelada por la eliminación de la cuenta. */
    private void notifyCounterpartiesByEmail(Long deletedUserId, List<RequestSnapshot> cancelled) {
        for (RequestSnapshot request : cancelled) {
            Long counterpartyId = deletedUserId.equals(request.clientId())
                    ? request.offererId()
                    : request.clientId();
            notificationCommandApi.notify(new NotificationCommand(
                    counterpartyId,
                    "request_cancelled_account_deleted",
                    "Solicitud cancelada: la otra parte eliminó su cuenta",
                    "La solicitud #" + request.id() + " fue cancelada porque la otra parte eliminó su cuenta en ServiYa.",
                    "SERVICE_REQUEST",
                    request.id(),
                    Set.of(Channel.EMAIL),
                    Map.of()));
        }
    }
}
