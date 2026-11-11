package com.parosurvivors.chat.conversations.application.dto;

import com.parosurvivors.chat.conversations.domain.ConversationStatus;
import com.parosurvivors.chat.conversations.domain.ConversationType;
import com.parosurvivors.chat.messaging.domain.MessageKind;
import java.time.Instant;

/** Fila del listado de «mis conversaciones». */
public record ConversationSummary(long id, ConversationType type, ConversationStatus status, Long contextId,
                                  String contextStatus, Instant lastMessageAt, long unreadCount,
                                  long counterpartId, String counterpartName, LastMessage lastMessage) {

    public record LastMessage(Long senderId, MessageKind kind, String body) {
    }
}
