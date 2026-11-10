package com.parosurvivors.chat.conversations.application.exceptions;

import com.parosurvivors.chat.shared.domain.DomainException;

public class ConversationNotFoundException extends DomainException {

    public ConversationNotFoundException(String message) {
        super("CHAT_CONVERSATION_NOT_FOUND", message);
    }

    public static ConversationNotFoundException byId(long id) {
        return new ConversationNotFoundException("No existe la conversación " + id);
    }

    public static ConversationNotFoundException byRequest(long requestId) {
        return new ConversationNotFoundException("No existe conversación para la solicitud " + requestId);
    }
}
