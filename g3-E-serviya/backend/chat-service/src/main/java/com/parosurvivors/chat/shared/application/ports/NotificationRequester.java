package com.parosurvivors.chat.shared.application.ports;

import java.util.Set;

/**
 * Comando de notificación ya compuesto por el chat (ADR-011): Notificaciones solo lo entrega. Nunca lleva el
 * cuerpo del mensaje. El adaptador lo emite como {@code notification.requested.v1} por el outbox.
 */
public interface NotificationRequester {

    void request(NotificationRequest request);

    record NotificationRequest(long recipientId, String type, String title, String message,
                               String entityType, long entityId, Set<String> channels) {
    }
}
