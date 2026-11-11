package com.parosurvivors.chat.messaging.application.services;

import com.parosurvivors.chat.shared.domain.DomainException;

/** RF-CHAT-30: un usuario suspendido (o eliminado) en la plataforma no puede enviar mensajes. */
public class UserCannotSendException extends DomainException {

    public UserCannotSendException() {
        super("CHAT_USER_SUSPENDED", "Tu cuenta no puede enviar mensajes");
    }
}
