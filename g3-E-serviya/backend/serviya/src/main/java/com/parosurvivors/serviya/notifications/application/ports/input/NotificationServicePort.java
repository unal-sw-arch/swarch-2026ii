package com.parosurvivors.serviya.notifications.application.ports.input;

import com.parosurvivors.serviya.notifications.domain.ChannelName;

import java.util.Map;
import java.util.Set;

/**
 * Puerto de entrada para PEDIR una notificación. Desde la extracción del microservicio
 * {@code notifications-service} (ESTADO §7.4 paso 1), el monolito ya no registra ni entrega notificaciones:
 * este puerto es la fachada CLIENTE que conservan users/requests/reports/admin, con la misma firma de
 * siempre, para que ningún llamador cambie.
 *
 * <p>La implementación deja un comando {@code notification.requested} en la tabla outbox DENTRO de la
 * transacción del llamador (si el negocio hace rollback, la notificación tampoco sale) y un relay lo
 * publica después en RabbitMQ. protectedData NO se persiste en claro: viaja sellado (AES-GCM).</p>
 */
public interface NotificationServicePort {

    /**
     * Pide notificar al usuario por los {@code channels} indicados (por nombre). Si {@code channels} es
     * {@code null} o vacío el servicio usa el canal por defecto INTERNAL. Asíncrono: la notificación aparece
     * en la bandeja del usuario poco después del commit, no dentro de esta llamada.
     */
    void notify(Long userId, String type, String title, String message, String entityType, Long entityId,
                Set<ChannelName> channels, Map<String, String> protectedData);
}
