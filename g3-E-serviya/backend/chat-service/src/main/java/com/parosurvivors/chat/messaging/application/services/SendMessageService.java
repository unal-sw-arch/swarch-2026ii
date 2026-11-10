package com.parosurvivors.chat.messaging.application.services;

import com.parosurvivors.chat.conversations.application.exceptions.ConversationNotFoundException;
import com.parosurvivors.chat.conversations.application.ports.output.ConversationRepository;
import com.parosurvivors.chat.conversations.domain.Conversation;
import com.parosurvivors.chat.conversations.domain.Participant;
import com.parosurvivors.chat.directory.application.ports.output.UserDirectory;
import com.parosurvivors.chat.directory.domain.DirectoryUser;
import com.parosurvivors.chat.messaging.application.dto.SendMessageResult;
import com.parosurvivors.chat.messaging.application.ports.output.MessageRepository;
import com.parosurvivors.chat.messaging.domain.Message;
import com.parosurvivors.chat.messaging.domain.MessageException;
import com.parosurvivors.chat.messaging.domain.MessageFlag;
import com.parosurvivors.chat.shared.application.ChatEvents;
import com.parosurvivors.chat.shared.application.RealtimeSafely;
import com.parosurvivors.chat.shared.application.exceptions.RateLimitExceededException;
import com.parosurvivors.chat.shared.application.ports.EventPublisher;
import com.parosurvivors.chat.shared.application.ports.NotificationRequester;
import com.parosurvivors.chat.shared.application.ports.NotificationRequester.NotificationRequest;
import com.parosurvivors.chat.shared.application.ports.OnlineUsers;
import com.parosurvivors.chat.shared.application.ports.RateLimiter;
import com.parosurvivors.chat.shared.application.ports.RealtimeNotifier;
import com.parosurvivors.chat.shared.application.ports.UnitOfWork;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Envío de un mensaje (RF-CHAT-10, 15, 16, 17, 30, 32). Orden (§5.4 de la guía):
 * <ol>
 *   <li>límite de envío;</li>
 *   <li>transacción: bloquear conversación → reconocer reenvío → autorizar → asignar seq → guardar mensaje,
 *       conversación y eventos de outbox;</li>
 *   <li>SOLO tras el commit: difusión en tiempo real. El ACK (el valor retornado) nunca sale antes del commit.</li>
 * </ol>
 */
public class SendMessageService {

    private static final int MAX_CLIENT_MESSAGE_ID = 36;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final UserDirectory users;
    private final EventPublisher events;
    private final NotificationRequester notifications;
    private final RealtimeNotifier realtime;
    private final OnlineUsers online;
    private final RateLimiter rateLimiter;
    private final UnitOfWork uow;
    private final Clock clock;
    private final int maxLength;

    public SendMessageService(ConversationRepository conversations, MessageRepository messages, UserDirectory users,
                              EventPublisher events, NotificationRequester notifications, RealtimeNotifier realtime,
                              OnlineUsers online, RateLimiter rateLimiter, UnitOfWork uow, Clock clock,
                              int maxLength) {
        this.conversations = conversations;
        this.messages = messages;
        this.users = users;
        this.events = events;
        this.notifications = notifications;
        this.realtime = realtime;
        this.online = online;
        this.rateLimiter = rateLimiter;
        this.uow = uow;
        this.clock = clock;
        this.maxLength = maxLength;
    }

    public record Command(long senderId, long conversationId, String clientMessageId, String body) {
    }

    private record Stored(SendMessageResult result, Set<Long> recipients) {
    }

    public SendMessageResult execute(Command cmd) {
        validateClientMessageId(cmd.clientMessageId());
        if (!rateLimiter.tryAcquire(cmd.senderId())) {
            throw new RateLimitExceededException();
        }

        Stored stored = uow.execute(() -> store(cmd));

        if (!stored.result().duplicate()) {
            Message message = stored.result().message();
            // El emisor también recibe el message.new para sus otras pestañas/dispositivos.
            Set<Long> everyone = new java.util.HashSet<>(stored.recipients());
            everyone.add(cmd.senderId());
            RealtimeSafely.run("message.new", () -> realtime.messageNew(message, everyone));
        }
        return stored.result();
    }

    private Stored store(Command cmd) {
        Conversation conversation = conversations.findByIdForUpdate(cmd.conversationId())
                .orElseThrow(() -> ConversationNotFoundException.byId(cmd.conversationId()));
        conversation.assertCanRead(cmd.senderId());

        // Reenvío por corte de red: se devuelve lo ya guardado, sin duplicar ni volver a entregar (RF-CHAT-16).
        var existing = messages.findByClientMessageId(cmd.conversationId(), cmd.senderId(), cmd.clientMessageId());
        if (existing.isPresent()) {
            return new Stored(resultOf(existing.get(), true), Set.of());
        }

        DirectoryUser sender = users.find(cmd.senderId()).orElse(null);
        if (sender != null && !sender.canSendMessages()) {
            throw new UserCannotSendException();
        }

        Instant now = clock.instant();
        long seq = conversation.registerMessage(cmd.senderId(), now); // valida participante y solo lectura
        Message message = messages.save(Message.text(cmd.conversationId(), seq, cmd.senderId(),
                cmd.clientMessageId(), cmd.body(), maxLength, now));
        conversations.save(conversation);

        Set<Long> recipients = conversation.othersThan(cmd.senderId()).stream()
                .map(Participant::userId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<Long> onlineNow = safeOnline(recipients);

        events.publish(ChatEvents.EXCHANGE, ChatEvents.MESSAGE_SENT, Map.of(
                "conversation_id", conversation.id(),
                "message_id", message.id(),
                "seq", seq,
                "sender_id", cmd.senderId(),
                "recipient_ids", List.copyOf(recipients),
                "recipient_online", onlineNow.containsAll(recipients),
                "flags", message.flags().stream().map(MessageFlag::name).sorted().toList()));

        // El chat decide y compone (ADR-011): aviso genérico, sin contenido ni vista previa (DP-04).
        String senderName = sender == null ? "un usuario" : sender.displayName();
        for (long recipient : recipients) {
            if (!onlineNow.contains(recipient)) {
                notifications.request(new NotificationRequest(recipient, "chat_message", "Nuevo mensaje",
                        "Tienes un mensaje nuevo de " + senderName, "CONVERSATION", conversation.id(),
                        Set.of("INTERNAL")));
            }
        }
        return new Stored(resultOf(message, false), recipients);
    }

    private Set<Long> safeOnline(Set<Long> recipients) {
        try {
            return online.onlineAmong(recipients);
        } catch (RuntimeException ex) {
            return Set.of(); // ante la duda se notifica: es preferible a perder el aviso
        }
    }

    private static SendMessageResult resultOf(Message message, boolean duplicate) {
        return new SendMessageResult(message,
                message.hasContactInfo() ? List.of(MessageFlag.CONTACT_INFO.name()) : List.of(), duplicate);
    }

    private static void validateClientMessageId(String id) {
        if (id == null || id.isBlank() || id.length() > MAX_CLIENT_MESSAGE_ID) {
            throw MessageException.invalidClientMessageId();
        }
    }
}
