package com.parosurvivors.chat.conversations.application.services;

import com.parosurvivors.chat.conversations.application.exceptions.ConversationNotFoundException;
import com.parosurvivors.chat.conversations.application.ports.output.ConversationRepository;
import com.parosurvivors.chat.conversations.domain.Conversation;
import com.parosurvivors.chat.shared.application.RealtimeSafely;
import com.parosurvivors.chat.shared.application.ports.RealtimeNotifier;
import java.util.Optional;

/** RF-CHAT-14: registra hasta qué mensaje leyó el participante y avisa al otro lado. */
public class MarkAsReadService {

    private final ConversationRepository conversations;
    private final RealtimeNotifier realtime;

    public MarkAsReadService(ConversationRepository conversations, RealtimeNotifier realtime) {
        this.conversations = conversations;
        this.realtime = realtime;
    }

    /** @return el seq efectivo, o vacío si no avanzó (ya estaba leído hasta ahí). */
    public Optional<Long> execute(long userId, long conversationId, long seq) {
        // Sin bloqueo de fila: el avance es monótono y atómico en la BD, y no compite con los envíos.
        Conversation c = conversations.findById(conversationId)
                .orElseThrow(() -> ConversationNotFoundException.byId(conversationId));
        Optional<Long> effective = c.markRead(userId, seq);
        effective.ifPresent(s -> {
            conversations.advanceReadPointer(conversationId, userId, s);
            RealtimeSafely.run("conversation.read_updated", () ->
                    realtime.readUpdated(conversationId, userId, s, c.participants().stream()
                            .map(p -> p.userId()).toList()));
        });
        return effective;
    }
}
