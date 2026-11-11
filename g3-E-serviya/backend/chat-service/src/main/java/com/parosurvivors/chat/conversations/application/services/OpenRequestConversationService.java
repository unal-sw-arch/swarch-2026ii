package com.parosurvivors.chat.conversations.application.services;

import com.parosurvivors.chat.conversations.application.exceptions.ConversationAlreadyExistsException;
import com.parosurvivors.chat.conversations.application.ports.output.ConversationRepository;
import com.parosurvivors.chat.conversations.domain.Conversation;
import com.parosurvivors.chat.conversations.domain.Participant;
import com.parosurvivors.chat.conversations.domain.TerminalRequestStatuses;
import com.parosurvivors.chat.shared.application.ChatEvents;
import com.parosurvivors.chat.shared.application.RealtimeSafely;
import com.parosurvivors.chat.shared.application.ports.EventPublisher;
import com.parosurvivors.chat.shared.application.ports.RealtimeNotifier;
import com.parosurvivors.chat.shared.application.ports.UnitOfWork;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Abre la conversación al crearse la solicitud (DP-01, RF-CHAT-01/02). Idempotente por solicitud. */
public class OpenRequestConversationService {

    private final ConversationRepository conversations;
    private final EventPublisher events;
    private final RealtimeNotifier realtime;
    private final UnitOfWork uow;
    private final TerminalRequestStatuses terminal;

    public OpenRequestConversationService(ConversationRepository conversations, EventPublisher events,
                                          RealtimeNotifier realtime, UnitOfWork uow,
                                          TerminalRequestStatuses terminal) {
        this.conversations = conversations;
        this.events = events;
        this.realtime = realtime;
        this.uow = uow;
        this.terminal = terminal;
    }

    public record Command(long requestId, long clientId, long offererId, String requestStatus, Instant occurredAt) {
    }

    public record Result(Conversation conversation, boolean created) {
    }

    public Result execute(Command cmd) {
        Result result;
        try {
            result = uow.execute(() -> create(cmd));
        } catch (ConversationAlreadyExistsException race) {
            // Dos eventos concurrentes para la misma solicitud: gana el índice único. La violación deja
            // marcada para rollback la transacción en curso, así que se relee en OTRA unidad de trabajo.
            result = uow.execute(() -> new Result(conversations.findByRequestId(cmd.requestId())
                    .orElseThrow(() -> race), false));
        }
        if (result.created()) {
            Conversation c = result.conversation();
            RealtimeSafely.run("conversation.created", () ->
                    realtime.conversationCreated(c, c.participants().stream().map(Participant::userId).toList()));
        }
        return result;
    }

    private Result create(Command cmd) {
        var existing = conversations.findByRequestId(cmd.requestId());
        if (existing.isPresent()) {
            return new Result(existing.get(), false);
        }
        Conversation fresh = Conversation.openForRequest(cmd.requestId(), cmd.clientId(), cmd.offererId(),
                cmd.requestStatus(), cmd.occurredAt(), terminal);
        Conversation saved = conversations.save(fresh); // puede lanzar ConversationAlreadyExistsException
        events.publish(ChatEvents.EXCHANGE, ChatEvents.CONVERSATION_CREATED, Map.of(
                "conversation_id", saved.id(),
                "type", saved.type().name(),
                "context_id", saved.contextId(),
                "participant_ids", List.of(cmd.clientId(), cmd.offererId())));
        return new Result(saved, true);
    }
}
