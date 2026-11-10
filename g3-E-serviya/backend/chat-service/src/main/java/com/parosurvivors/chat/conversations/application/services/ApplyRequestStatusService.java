package com.parosurvivors.chat.conversations.application.services;

import com.parosurvivors.chat.conversations.application.exceptions.ConversationNotFoundException;
import com.parosurvivors.chat.conversations.application.ports.output.ConversationRepository;
import com.parosurvivors.chat.conversations.domain.Conversation;
import com.parosurvivors.chat.conversations.domain.ConversationStatus;
import com.parosurvivors.chat.conversations.domain.Participant;
import com.parosurvivors.chat.conversations.domain.TerminalRequestStatuses;
import com.parosurvivors.chat.shared.application.ChatEvents;
import com.parosurvivors.chat.shared.application.RealtimeSafely;
import com.parosurvivors.chat.shared.application.ports.EventPublisher;
import com.parosurvivors.chat.shared.application.ports.RealtimeNotifier;
import com.parosurvivors.chat.shared.application.ports.UnitOfWork;
import java.time.Instant;
import java.util.Map;

/**
 * Refleja cada cambio de estado de la solicitud (ADR-012): actualiza el estado copiado para los filtros y, si es
 * terminal, pasa la conversación a solo lectura definitiva (DP-02). El chat no sabe POR QUÉ cambió.
 */
public class ApplyRequestStatusService {

    private final ConversationRepository conversations;
    private final EventPublisher events;
    private final RealtimeNotifier realtime;
    private final UnitOfWork uow;
    private final TerminalRequestStatuses terminal;

    public ApplyRequestStatusService(ConversationRepository conversations, EventPublisher events,
                                     RealtimeNotifier realtime, UnitOfWork uow, TerminalRequestStatuses terminal) {
        this.conversations = conversations;
        this.events = events;
        this.realtime = realtime;
        this.uow = uow;
        this.terminal = terminal;
    }

    public record Command(long requestId, String status, Instant occurredAt) {
    }

    /**
     * @throws ConversationNotFoundException si aún no existe la conversación (el evento llegó antes que
     *         {@code created}): el consumidor reintenta y, agotados los reintentos, va a la DLQ.
     */
    public boolean execute(Command cmd) {
        Conversation updated = uow.execute(() -> {
            Conversation c = conversations.findByRequestIdForUpdate(cmd.requestId())
                    .orElseThrow(() -> ConversationNotFoundException.byRequest(cmd.requestId()));
            ConversationStatus before = c.status();
            if (!c.applyRequestStatus(cmd.status(), cmd.occurredAt(), terminal)) {
                return null;
            }
            conversations.save(c);
            if (before == ConversationStatus.ACTIVE && c.status() == ConversationStatus.READ_ONLY) {
                events.publish(ChatEvents.EXCHANGE, ChatEvents.CONVERSATION_READ_ONLY, Map.of(
                        "conversation_id", c.id(),
                        "reason", "REQUEST_" + cmd.status().toUpperCase()));
            }
            return c;
        });
        if (updated == null) {
            return false;
        }
        RealtimeSafely.run("conversation.status_changed", () -> realtime.conversationStatusChanged(
                updated, updated.participants().stream().map(Participant::userId).toList()));
        return true;
    }
}
