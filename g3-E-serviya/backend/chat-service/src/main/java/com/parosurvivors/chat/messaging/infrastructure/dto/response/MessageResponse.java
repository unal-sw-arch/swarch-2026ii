package com.parosurvivors.chat.messaging.infrastructure.dto.response;

import com.parosurvivors.chat.messaging.domain.Message;
import com.parosurvivors.chat.messaging.domain.MessageFlag;
import java.time.Instant;
import java.util.List;

public record MessageResponse(Long id, long conversationId, long seq, Long senderId, String kind, String body,
                              List<String> flags, Instant createdAt) {

    public static MessageResponse of(Message m) {
        return new MessageResponse(m.id(), m.conversationId(), m.seq(), m.senderId(), m.kind().name(), m.body(),
                m.flags().stream().map(MessageFlag::name).sorted().toList(), m.createdAt());
    }
}
