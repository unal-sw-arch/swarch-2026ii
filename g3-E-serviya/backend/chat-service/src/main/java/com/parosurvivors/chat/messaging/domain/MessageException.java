package com.parosurvivors.chat.messaging.domain;

import com.parosurvivors.chat.shared.domain.DomainException;

public class MessageException extends DomainException {

    private MessageException(String code, String message) {
        super(code, message);
    }

    public static MessageException empty() {
        return new MessageException("CHAT_MESSAGE_EMPTY", "El mensaje no puede estar vacío");
    }

    public static MessageException tooLong(int max) {
        return new MessageException("CHAT_MESSAGE_TOO_LONG", "El mensaje supera los " + max + " caracteres");
    }

    public static MessageException invalidClientMessageId() {
        return new MessageException("CHAT_INVALID_CLIENT_MESSAGE_ID",
                "client_message_id es obligatorio y debe tener máximo 36 caracteres");
    }
}
