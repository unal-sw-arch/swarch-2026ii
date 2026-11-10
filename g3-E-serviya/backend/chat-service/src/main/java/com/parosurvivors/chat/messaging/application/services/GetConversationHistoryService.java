package com.parosurvivors.chat.messaging.application.services;

import com.parosurvivors.chat.conversations.application.exceptions.ConversationNotFoundException;
import com.parosurvivors.chat.conversations.application.ports.output.ConversationRepository;
import com.parosurvivors.chat.messaging.application.ports.output.MessageRepository;
import com.parosurvivors.chat.messaging.domain.Message;
import java.util.List;

/** RF-CHAT-12: historial paginado por secuencia (cursor), del más reciente hacia atrás. */
public class GetConversationHistoryService {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    private final ConversationRepository conversations;
    private final MessageRepository messages;

    public GetConversationHistoryService(ConversationRepository conversations, MessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    public List<Message> execute(long userId, long conversationId, Long beforeSeq, Long afterSeq, Integer limit) {
        if (beforeSeq != null && afterSeq != null) {
            throw new InvalidPaginationException();
        }
        conversations.findById(conversationId)
                .orElseThrow(() -> ConversationNotFoundException.byId(conversationId))
                .assertCanRead(userId);
        int size = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        return messages.findPage(conversationId, beforeSeq, afterSeq, size);
    }
}
