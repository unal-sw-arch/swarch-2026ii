package com.parosurvivors.chat.conversations.infrastructure.dto.response;

import com.parosurvivors.chat.conversations.application.dto.ConversationView;
import java.time.Instant;
import java.util.List;

public record ConversationDetailResponse(long id, String type, String status, Long contextId, String contextStatus,
                                         long lastSeq, Instant lastMessageAt, Instant createdAt, long unreadCount,
                                         long myLastReadSeq, List<Participant> participants) {

    public record Participant(long userId, String role, long lastReadSeq) {
    }

    public static ConversationDetailResponse of(ConversationView v) {
        return new ConversationDetailResponse(v.id(), v.type().name(), v.status().name(), v.contextId(),
                v.contextStatus(), v.lastSeq(), v.lastMessageAt(), v.createdAt(), v.unreadCount(), v.myLastReadSeq(),
                v.participants().stream()
                        .map(p -> new Participant(p.userId(), p.role().name(), p.lastReadSeq())).toList());
    }
}
