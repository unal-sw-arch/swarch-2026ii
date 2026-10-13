package com.parosurvivors.notifications.application.ports.output;

import java.util.Map;

/**
 * Puerto de salida al proveedor de correo. A diferencia del monolito, recibe el correo del destinatario
 * ya resuelto (viaja en el mensaje): el servicio no depende de identity/users para enviar.
 */
public interface EmailPort {

    /** Envía el correo. NUNCA lanza: ante cualquier fallo devuelve {@code false}. */
    boolean send(String recipientEmail, String type, String title, String message,
                 Map<String, String> protectedData);
}
