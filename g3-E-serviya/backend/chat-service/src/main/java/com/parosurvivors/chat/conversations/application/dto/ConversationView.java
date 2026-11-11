package com.parosurvivors.chat.conversations.application.dto;

import com.parosurvivors.chat.conversations.domain.Conversation;
import com.parosurvivors.chat.conversations.domain.ConversationStatus;
import com.parosurvivors.chat.conversations.domain.ConversationType;
import com.parosurvivors.chat.conversations.domain.ParticipantRole;
import java.time.Instant;
import java.util.List;

/** Detalle de una conversación desde el punto de vista de {@code viewerId}. */
public record ConversationView(long id, ConversationType type, ConversationStatus status, Long contextId,
                               String contextStatus, long lastSeq, Instant lastMessageAt, Instant createdAt,
                               long unreadCount, long myLastReadSeq, List<ParticipantView> participants) {

    public record ParticipantView(long userId, ParticipantRole role, long lastReadSeq) {
    }

    public static ConversationView of(Conversation c, long viewerId) {
        return new ConversationView(c.id(), c.type(), c.status(), c.contextId(), c.contextStatus(), c.lastSeq(),
                c.lastMessageAt(), c.createdAt(), c.unreadFor(viewerId),
                c.participant(viewerId).orElseThrow().lastReadSeq(),
                c.participants().stream()
                        .map(p -> new ParticipantView(p.userId(), p.role(), p.lastReadSeq())).toList());
    }
}
