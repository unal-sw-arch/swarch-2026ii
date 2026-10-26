package com.parosurvivors.notifications.application.ports.input;

import com.parosurvivors.notifications.application.dto.command.NotifyCommand;

/**
 * Puerto de entrada de NotificationService — registra una notificación y orquesta su entrega multicanal.
 * protectedData NO se persiste; el adaptador del canal arma el mensaje final.
 */
public interface NotificationServicePort {

    /**
     * Registra la notificación y la entrega por los canales indicados (por nombre, no por id). Si no se
     * indica ningún canal se usa el canal por defecto INTERNAL.
     *
     * <p><b>Idempotente</b> por {@code sourceEventId}: el bus entrega "al menos una vez", así que el mismo
     * mensaje puede llegar dos veces; la segunda no crea nada.</p>
     *
     * @return {@code true} si se registró; {@code false} si el evento ya se había procesado (duplicado).
     */
    boolean notify(NotifyCommand command);
}
