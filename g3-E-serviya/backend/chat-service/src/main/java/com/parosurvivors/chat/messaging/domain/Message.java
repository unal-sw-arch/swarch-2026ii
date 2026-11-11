package com.parosurvivors.chat.messaging.domain;

import java.time.Instant;
import java.util.Set;

/** Mensaje inmutable (invariante 5): nunca cambia de contenido tras crearse. */
public record Message(Long id, long conversationId, long seq, Long senderId, String clientMessageId,
                      MessageKind kind, Set<MessageFlag> flags, String body, Instant createdAt) {

    public Message {
        flags = Set.copyOf(flags);
    }

    /** Mensaje de texto de un participante. Valida longitud y marca datos de contacto (RF-CHAT-17 y 32). */
    public static Message text(long conversationId, long seq, long senderId, String clientMessageId, String body,
                               int maxLength, Instant at) {
        if (body == null || body.isBlank()) {
            throw MessageException.empty();
        }
        String trimmed = body.strip();
        if (trimmed.length() > maxLength) {
            throw MessageException.tooLong(maxLength);
        }
        Set<MessageFlag> flags = ContactInfoDetector.looksLikeContactInfo(trimmed)
                ? Set.of(MessageFlag.CONTACT_INFO) : Set.of();
        return new Message(null, conversationId, seq, senderId, clientMessageId, MessageKind.TEXT, flags, trimmed, at);
    }

    /** Mensaje de sistema (hitos de la solicitud): sin emisor ni id de cliente. */
    public static Message system(long conversationId, long seq, String body, Instant at) {
        return new Message(null, conversationId, seq, null, null, MessageKind.SYSTEM, Set.of(), body, at);
    }

    public boolean hasContactInfo() {
        return flags.contains(MessageFlag.CONTACT_INFO);
    }
}
