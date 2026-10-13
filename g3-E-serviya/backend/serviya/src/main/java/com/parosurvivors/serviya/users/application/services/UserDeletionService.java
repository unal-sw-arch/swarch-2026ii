package com.parosurvivors.serviya.users.application.services;

import com.parosurvivors.serviya.notifications.application.ports.input.NotificationServicePort;
import com.parosurvivors.serviya.notifications.domain.ChannelName;
import com.parosurvivors.serviya.requests.application.ports.input.ServiceRequestCommandServicePort;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.parosurvivors.serviya.services.application.ports.input.MarketplaceServicePort;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
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
    private final MarketplaceServicePort marketplaceServicePort;
    private final ServiceRequestCommandServicePort serviceRequestCommandServicePort;
    private final NotificationServicePort notificationServicePort;

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
        // (2) Si es oferente, ocultar todos sus servicios del buscador (no-op si no tiene servicios).
        marketplaceServicePort.deactivateAllByOfferer(userId);
        // (3) Cancelar las solicitudes activas donde participa (como cliente u oferente) para no dejar huérfanas.
        //     cancelRequest ya publica RequestStatusChangedEvent (métricas) y avisa IN_APP a la contraparte.
        List<ServiceRequest> cancelled = serviceRequestCommandServicePort.cancelActiveRequestsForUser(userId);
        // (4) Notificación dual-canal: el aviso IN_APP ("Solicitud cancelada") ya lo emite cancelRequest;
        //     aquí se completa el otro canal (EMAIL) explicando el motivo real —la cuenta fue eliminada—,
        //     que el aviso genérico de cancelación no transmite. Así la contraparte queda informada por
        //     ambos canales sin recibir dos veces la misma notificación interna.
        notifyCounterpartiesByEmail(userId, cancelled);
    }

    /** Avisa por EMAIL a la contraparte de cada solicitud cancelada por la eliminación de la cuenta. */
    private void notifyCounterpartiesByEmail(Long deletedUserId, List<ServiceRequest> cancelled) {
        for (ServiceRequest request : cancelled) {
            Long counterpartyId = deletedUserId.equals(request.getClientId())
                    ? request.getOffererId()
                    : request.getClientId();
            notificationServicePort.notify(
                    counterpartyId,
                    "request_cancelled_account_deleted",
                    "Solicitud cancelada: la otra parte eliminó su cuenta",
                    "La solicitud #" + request.getId() + " fue cancelada porque la otra parte eliminó su cuenta en ServiYa.",
                    "SERVICE_REQUEST",
                    request.getId(),
                    Set.of(ChannelName.EMAIL),
                    Map.of());
        }
    }
}
