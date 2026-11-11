package com.parosurvivors.chat.messaging.application.services;

import com.parosurvivors.chat.conversations.application.ports.output.ConversationRepository;
import com.parosurvivors.chat.messaging.application.ports.output.MessageRepository;
import com.parosurvivors.chat.messaging.domain.Message;
import java.util.ArrayList;
import java.util.List;

/**
 * Recuperación tras reconectar (RNF-CHAT-16): por cada conversación que el cliente conoce, devuelve lo que
 * llegó después de su último seq. Ignora las que no existen o donde no participa.
 */
public class SyncMessagesService {

    public static final int PER_CONVERSATION_LIMIT = 200;

    private final ConversationRepository conversations;
    private final MessageRepository messages;

    public SyncMessagesService(ConversationRepository conversations, MessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    public record Cursor(long conversationId, long lastSeq) {
    }

    public record ConversationSync(long conversationId, List<Message> messages, boolean hasMore) {
    }

    public List<ConversationSync> execute(long userId, List<Cursor> cursors) {
        List<ConversationSync> out = new ArrayList<>();
        for (Cursor cursor : cursors) {
            var conversation = conversations.findById(cursor.conversationId());
            if (conversation.isEmpty() || !conversation.get().isParticipant(userId)) {
                continue;
            }
            if (conversation.get().lastSeq() <= cursor.lastSeq()) {
                continue; // al día
            }
            List<Message> page = messages.findPage(cursor.conversationId(), null, cursor.lastSeq(),
                    PER_CONVERSATION_LIMIT + 1);
            boolean hasMore = page.size() > PER_CONVERSATION_LIMIT;
            out.add(new ConversationSync(cursor.conversationId(),
                    hasMore ? page.subList(0, PER_CONVERSATION_LIMIT) : page, hasMore));
        }
        return out;
    }
}
