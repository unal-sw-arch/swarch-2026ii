package com.parosurvivors.chat.conversations.application.dto;

import java.time.Instant;

/** Cursor de paginación por (last_message_at, id): los mensajes nuevos no desordenan las páginas. */
public record ConversationCursor(Instant lastMessageAt, long id) {
}
