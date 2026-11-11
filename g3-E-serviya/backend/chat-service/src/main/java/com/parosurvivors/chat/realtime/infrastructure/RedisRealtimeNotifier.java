package com.parosurvivors.chat.realtime.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parosurvivors.chat.conversations.domain.Conversation;
import com.parosurvivors.chat.conversations.domain.Participant;
import com.parosurvivors.chat.messaging.domain.Message;
import com.parosurvivors.chat.messaging.domain.MessageFlag;
import com.parosurvivors.chat.shared.application.ports.RealtimeNotifier;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Difunde por el Pub/Sub propio del chat (instancia C). La instancia que tiene conectado al usuario recibe el
 * frame y lo entrega a sus sesiones. Best effort: un fallo aquí lo absorbe el caso de uso y el cliente recupera lo
 * perdido por {@code sync}/REST (RNF-CHAT-16).
 */
@Component
class RedisRealtimeNotifier implements RealtimeNotifier {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    RedisRealtimeNotifier(@Qualifier("pubSubRedis") StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @Override
    public void messageNew(Message m, Collection<Long> userIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", m.conversationId());
        payload.put("message_id", m.id());
        payload.put("seq", m.seq());
        payload.put("sender_id", m.senderId());
        payload.put("kind", m.kind().name());
        payload.put("body", m.body());
        payload.put("flags", m.flags().stream().map(MessageFlag::name).sorted().toList());
        payload.put("created_at", m.createdAt().toString());
        publish("message.new", payload, userIds);
    }

    @Override
    public void readUpdated(long conversationId, long readerId, long seq, Collection<Long> userIds) {
        publish("conversation.read_updated",
                Map.of("conversation_id", conversationId, "user_id", readerId, "seq", seq), userIds);
    }

    @Override
    public void conversationCreated(Conversation c, Collection<Long> userIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", c.id());
        payload.put("type", c.type().name());
        payload.put("context_id", c.contextId());
        payload.put("status", c.status().name());
        payload.put("context_status", c.contextStatus());
        payload.put("participant_ids", c.participants().stream().map(Participant::userId).toList());
        publish("conversation.created", payload, userIds);
    }

    @Override
    public void conversationStatusChanged(Conversation c, Collection<Long> userIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversation_id", c.id());
        payload.put("status", c.status().name());
        payload.put("context_status", c.contextStatus());
        publish("conversation.status_changed", payload, userIds);
    }

    private void publish(String type, Map<String, Object> payload, Collection<Long> userIds) {
        String frame = Frames.of(mapper, type, null, payload);
        // Se intenta con todos aunque alguno falle: un error aislado no debe privar al resto del mensaje.
        RuntimeException first = null;
        for (long userId : List.copyOf(userIds)) {
            try {
                redis.convertAndSend(RealtimeChannels.forUser(userId), frame);
            } catch (RuntimeException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
