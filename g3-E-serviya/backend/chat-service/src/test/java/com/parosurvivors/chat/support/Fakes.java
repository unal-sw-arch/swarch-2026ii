package com.parosurvivors.chat.support;

import com.parosurvivors.chat.conversations.application.dto.ConversationCursor;
import com.parosurvivors.chat.conversations.application.dto.ConversationFilter;
import com.parosurvivors.chat.conversations.application.dto.ConversationSummary;
import com.parosurvivors.chat.conversations.application.exceptions.ConversationAlreadyExistsException;
import com.parosurvivors.chat.conversations.application.ports.output.ConversationReadModel;
import com.parosurvivors.chat.conversations.application.ports.output.ConversationRepository;
import com.parosurvivors.chat.conversations.domain.Conversation;
import com.parosurvivors.chat.conversations.domain.ConversationType;
import com.parosurvivors.chat.conversations.domain.Participant;
import com.parosurvivors.chat.directory.application.ports.output.UserDirectory;
import com.parosurvivors.chat.directory.domain.DirectoryUser;
import com.parosurvivors.chat.messaging.application.ports.output.MessageRepository;
import com.parosurvivors.chat.messaging.domain.Message;
import com.parosurvivors.chat.shared.application.ports.EventPublisher;
import com.parosurvivors.chat.shared.application.ports.NotificationRequester;
import com.parosurvivors.chat.shared.application.ports.OnlineUsers;
import com.parosurvivors.chat.shared.application.ports.RateLimiter;
import com.parosurvivors.chat.shared.application.ports.RealtimeNotifier;
import com.parosurvivors.chat.shared.application.ports.UnitOfWork;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Implementaciones en memoria de los puertos (§14.1 de la guía): los casos de uso se prueban sin infraestructura. */
public final class Fakes {

    private Fakes() {
    }

    public static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    /** Guarda y devuelve COPIAS: si un caso de uso olvida {@code save}, el cambio se pierde, como en una BD real. */
    public static class InMemoryConversations implements ConversationRepository {
        public final Map<Long, Conversation> store = new HashMap<>();
        public final List<Long> lockedIds = new ArrayList<>();
        private long nextId = 1;

        @Override
        public Conversation save(Conversation c) {
            Long id = c.id();
            if (id == null) {
                if (c.type() == ConversationType.REQUEST && findByRequestId(c.contextId()).isPresent()) {
                    throw new ConversationAlreadyExistsException(c.contextId());
                }
                id = nextId++;
            }
            Conversation stored = copy(id, c);
            store.put(id, stored);
            return copy(id, stored);
        }

        @Override
        public Optional<Conversation> findById(long id) {
            return Optional.ofNullable(store.get(id)).map(c -> copy(id, c));
        }

        @Override
        public Optional<Conversation> findByIdForUpdate(long id) {
            lockedIds.add(id);
            return findById(id);
        }

        @Override
        public Optional<Conversation> findByRequestId(long requestId) {
            return store.values().stream()
                    .filter(c -> c.type() == ConversationType.REQUEST && c.contextId() == requestId)
                    .findFirst().map(c -> copy(c.id(), c));
        }

        @Override
        public Optional<Conversation> findByRequestIdForUpdate(long requestId) {
            Optional<Conversation> found = findByRequestId(requestId);
            found.ifPresent(c -> lockedIds.add(c.id()));
            return found;
        }

        @Override
        public void advanceReadPointer(long conversationId, long userId, long seq) {
            Conversation c = store.get(conversationId);
            c.markRead(userId, seq);
        }

        private static Conversation copy(long id, Conversation c) {
            List<Participant> ps = c.participants().stream()
                    .map(p -> new Participant(p.userId(), p.role(), p.lastReadSeq(), p.joinedAt())).toList();
            return new Conversation(id, c.type(), c.contextId(), c.status(), c.contextStatus(),
                    c.contextStatusAt(), c.lastSeq(), c.lastMessageAt(), c.createdAt(), ps);
        }
    }

    public static class InMemoryMessages implements MessageRepository {
        public final List<Message> store = new ArrayList<>();
        private long nextId = 1;

        @Override
        public Message save(Message m) {
            Message saved = new Message(nextId++, m.conversationId(), m.seq(), m.senderId(), m.clientMessageId(),
                    m.kind(), m.flags(), m.body(), m.createdAt());
            store.add(saved);
            return saved;
        }

        @Override
        public Optional<Message> findByClientMessageId(long conversationId, long senderId, String cid) {
            return store.stream().filter(m -> m.conversationId() == conversationId
                    && Long.valueOf(senderId).equals(m.senderId()) && cid.equals(m.clientMessageId())).findFirst();
        }

        @Override
        public List<Message> findPage(long conversationId, Long beforeSeq, Long afterSeq, int limit) {
            List<Message> all = store.stream().filter(m -> m.conversationId() == conversationId)
                    .sorted(Comparator.comparingLong(Message::seq)).toList();
            if (afterSeq != null) {
                return all.stream().filter(m -> m.seq() > afterSeq).limit(limit).toList();
            }
            List<Message> older = all.stream().filter(m -> beforeSeq == null || m.seq() < beforeSeq).toList();
            return older.subList(Math.max(0, older.size() - limit), older.size());
        }
    }

    public static class RecordingEvents implements EventPublisher {
        public record Published(String exchange, String routingKey, Map<String, Object> data) {
        }

        public final List<Published> published = new ArrayList<>();

        @Override
        public void publish(String exchange, String routingKey, Map<String, Object> data) {
            published.add(new Published(exchange, routingKey, data));
        }

        public List<Published> of(String routingKey) {
            return published.stream().filter(p -> p.routingKey().equals(routingKey)).toList();
        }
    }

    public static class RecordingNotifications implements NotificationRequester {
        public final List<NotificationRequest> requests = new ArrayList<>();

        @Override
        public void request(NotificationRequest request) {
            requests.add(request);
        }
    }

    public static class RecordingRealtime implements RealtimeNotifier {
        public List<String> log = new ArrayList<>();
        public final List<Message> messagesSent = new ArrayList<>();
        public final Map<String, Set<Long>> recipientsByEvent = new HashMap<>();
        public boolean failing;

        private void record(String event, Collection<Long> users) {
            if (failing) {
                throw new IllegalStateException("redis caído");
            }
            log.add(event);
            recipientsByEvent.put(event, new HashSet<>(users));
        }

        @Override
        public void messageNew(Message message, Collection<Long> userIds) {
            record("message.new", userIds);
            messagesSent.add(message);
        }

        @Override
        public void readUpdated(long conversationId, long readerId, long seq, Collection<Long> userIds) {
            record("conversation.read_updated", userIds);
        }

        @Override
        public void conversationCreated(Conversation conversation, Collection<Long> userIds) {
            record("conversation.created", userIds);
        }

        @Override
        public void conversationStatusChanged(Conversation conversation, Collection<Long> userIds) {
            record("conversation.status_changed", userIds);
        }
    }

    public static class StubOnlineUsers implements OnlineUsers {
        public final Set<Long> online = new HashSet<>();
        public boolean failing;

        @Override
        public Set<Long> onlineAmong(Collection<Long> userIds) {
            if (failing) {
                throw new IllegalStateException("pubsub caído");
            }
            Set<Long> result = new HashSet<>(userIds);
            result.retainAll(online);
            return result;
        }
    }

    public static class StubRateLimiter implements RateLimiter {
        public boolean allow = true;

        @Override
        public boolean tryAcquire(long userId) {
            return allow;
        }
    }

    public static class InMemoryUsers implements UserDirectory {
        public final Map<Long, DirectoryUser> store = new HashMap<>();

        @Override
        public Optional<DirectoryUser> find(long userId) {
            return Optional.ofNullable(store.get(userId));
        }

        @Override
        public void save(DirectoryUser user) {
            store.put(user.userId(), user);
        }
    }

    /** Ejecuta directo y registra el orden relativo para comprobar «tiempo real SOLO tras el commit». */
    public static class DirectUnitOfWork implements UnitOfWork {
        public final List<String> timeline;
        public boolean inTransaction;

        public DirectUnitOfWork(List<String> timeline) {
            this.timeline = timeline;
        }

        @Override
        public <T> T execute(Supplier<T> work) {
            inTransaction = true;
            try {
                T result = work.get();
                timeline.add("commit");
                return result;
            } finally {
                inTransaction = false;
            }
        }
    }

    public static class CannedReadModel implements ConversationReadModel {
        public List<ConversationSummary> rows = List.of();
        public ConversationFilter lastFilter;
        public int lastLimit;
        public ConversationCursor lastCursor;

        @Override
        public List<ConversationSummary> findForUser(long userId, ConversationFilter filter,
                                                     ConversationCursor after, int limit) {
            lastFilter = filter;
            lastLimit = limit;
            lastCursor = after;
            return rows.stream().limit(limit).toList();
        }
    }
}
