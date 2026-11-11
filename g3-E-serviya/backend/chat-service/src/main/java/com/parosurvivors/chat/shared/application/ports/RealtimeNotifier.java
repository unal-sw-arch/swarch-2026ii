package com.parosurvivors.chat.shared.application.ports;

import com.parosurvivors.chat.conversations.domain.Conversation;
import com.parosurvivors.chat.messaging.domain.Message;
import java.util.Collection;

/**
 * Difusión en tiempo real a los usuarios conectados (en cualquier instancia). Es best effort: si falla, los
 * mensajes ya están guardados y el cliente los recupera por {@code sync}/REST. Se invoca SIEMPRE tras el commit.
 */
public interface RealtimeNotifier {

    void messageNew(Message message, Collection<Long> userIds);

    void readUpdated(long conversationId, long readerId, long seq, Collection<Long> userIds);

    void conversationCreated(Conversation conversation, Collection<Long> userIds);

    void conversationStatusChanged(Conversation conversation, Collection<Long> userIds);
}
