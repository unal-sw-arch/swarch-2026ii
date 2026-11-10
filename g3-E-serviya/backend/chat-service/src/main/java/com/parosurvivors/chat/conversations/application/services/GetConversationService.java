package com.parosurvivors.chat.conversations.application.services;

import com.parosurvivors.chat.conversations.application.dto.ConversationView;
import com.parosurvivors.chat.conversations.application.exceptions.ConversationNotFoundException;
import com.parosurvivors.chat.conversations.application.ports.output.ConversationRepository;
import com.parosurvivors.chat.conversations.domain.Conversation;

/** Detalle por id o por solicitud. Solo para participantes (RF-CHAT-04). */
public class GetConversationService {

    private final ConversationRepository conversations;

    public GetConversationService(ConversationRepository conversations) {
        this.conversations = conversations;
    }

    public ConversationView byId(long userId, long conversationId) {
        Conversation c = conversations.findById(conversationId)
                .orElseThrow(() -> ConversationNotFoundException.byId(conversationId));
        c.assertCanRead(userId);
        return ConversationView.of(c, userId);
    }

    public ConversationView byRequest(long userId, long requestId) {
        Conversation c = conversations.findByRequestId(requestId)
                .orElseThrow(() -> ConversationNotFoundException.byRequest(requestId));
        c.assertCanRead(userId);
        return ConversationView.of(c, userId);
    }
}
