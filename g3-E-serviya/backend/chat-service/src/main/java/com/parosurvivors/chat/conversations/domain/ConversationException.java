package com.parosurvivors.chat.conversations.domain;

import com.parosurvivors.chat.shared.domain.DomainException;

public class ConversationException extends DomainException {

    private ConversationException(String code, String message) {
        super(code, message);
    }

    public static ConversationException notParticipant() {
        return new ConversationException("CHAT_NOT_PARTICIPANT", "El usuario no participa en la conversación");
    }

    public static ConversationException readOnly() {
        return new ConversationException("CHAT_CONVERSATION_READ_ONLY", "La conversación es de solo lectura");
    }

    public static ConversationException invalid(String message) {
        return new ConversationException("CHAT_INVALID_CONVERSATION", message);
    }
}
