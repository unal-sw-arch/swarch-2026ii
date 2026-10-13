package com.parosurvivors.serviya.notifications.application.ports.output;

import java.util.Optional;

/**
 * Puerto de salida: correo del destinatario. Se resuelve en el monolito (dueño de identity) y viaja en el
 * mensaje, para que notifications-service no dependa de users para enviar.
 */
public interface RecipientEmailPort {

    Optional<String> findEmail(Long userId);
}
