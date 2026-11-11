package com.parosurvivors.chat.conversations.infrastructure.dto.response;

import com.parosurvivors.chat.conversations.application.dto.ConversationSummary;
import java.time.Instant;

public record ConversationSummaryResponse(long id, String type, String status, Long contextId, String contextStatus,
                                          Instant lastMessageAt, long unreadCount, Counterpart counterpart,
                                          LastMessage lastMessage) {

    public record Counterpart(long userId, String displayName) {
    }

    public record LastMessage(Long senderId, String kind, String body) {
    }

    public static ConversationSummaryResponse of(ConversationSummary s) {
        return new ConversationSummaryResponse(s.id(), s.type().name(), s.status().name(), s.contextId(),
                s.contextStatus(), s.lastMessageAt(), s.unreadCount(),
                new Counterpart(s.counterpartId(), s.counterpartName()),
                s.lastMessage() == null ? null : new LastMessage(s.lastMessage().senderId(),
                        s.lastMessage().kind().name(), s.lastMessage().body()));
    }
}
