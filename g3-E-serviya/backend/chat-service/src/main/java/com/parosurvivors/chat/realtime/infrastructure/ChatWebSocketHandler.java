package com.parosurvivors.chat.realtime.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parosurvivors.chat.conversations.application.services.MarkAsReadService;
import com.parosurvivors.chat.directory.application.ports.output.UserDirectory;
import com.parosurvivors.chat.messaging.application.services.SendMessageService;
import com.parosurvivors.chat.messaging.application.services.SyncMessagesService;
import com.parosurvivors.chat.messaging.domain.Message;
import com.parosurvivors.chat.messaging.domain.MessageFlag;
import com.parosurvivors.chat.realtime.application.ports.TicketStoreUnavailableException;
import com.parosurvivors.chat.realtime.application.services.WsTicketService;
import com.parosurvivors.chat.shared.domain.DomainException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Protocolo WebSocket del chat (guía §8.2): una conexión por cliente, multiplexando todas sus conversaciones.
 * La autenticación es por ticket de un solo uso (ADR-010). Cierres: {@code 4401} ticket inválido/usado/vencido,
 * {@code 4403} usuario suspendido o eliminado, {@code 1013} servicio de tickets no disponible, {@code 1009} frame
 * demasiado grande (lo aplica el contenedor con el límite de 8 KB).
 *
 * <p>Las operaciones se ejecutan por los MISMOS casos de uso que REST, así que las reglas (participante, solo
 * lectura, idempotencia, límite de envío) son idénticas por ambos canales.</p>
 */
@Component
class ChatWebSocketHandler extends TextWebSocketHandler {

    static final CloseStatus INVALID_TICKET = new CloseStatus(4401, "invalid ticket");
    static final CloseStatus USER_SUSPENDED = new CloseStatus(4403, "user suspended");
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;
    private static final String USER_ID_ATTR = "chat.userId";
    private static final String DECORATED_ATTR = "chat.session";

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final WsTicketService tickets;
    private final UserDirectory users;
    private final ConnectionRegistry registry;
    private final SendMessageService send;
    private final MarkAsReadService markAsRead;
    private final SyncMessagesService sync;
    private final ObjectMapper mapper;

    ChatWebSocketHandler(WsTicketService tickets, UserDirectory users, ConnectionRegistry registry,
                         SendMessageService send, MarkAsReadService markAsRead, SyncMessagesService sync,
                         ObjectMapper mapper) {
        this.tickets = tickets;
        this.users = users;
        this.registry = registry;
        this.send = send;
        this.markAsRead = markAsRead;
        this.sync = sync;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String ticket = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("ticket");
        Optional<Long> userId;
        try {
            userId = tickets.consume(ticket);
        } catch (TicketStoreUnavailableException e) {
            session.close(new CloseStatus(1013, "try again later"));
            return;
        }
        if (userId.isEmpty()) {
            session.close(INVALID_TICKET);
            return;
        }
        if (users.find(userId.get()).map(u -> !u.canSendMessages()).orElse(false)) {
            session.close(USER_SUSPENDED);
            return;
        }
        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS,
                SEND_BUFFER_LIMIT_BYTES);
        session.getAttributes().put(USER_ID_ATTR, userId.get());
        session.getAttributes().put(DECORATED_ATTR, decorated);
        registry.add(userId.get(), decorated);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = (Long) session.getAttributes().get(USER_ID_ATTR);
        WebSocketSession out = (WebSocketSession) session.getAttributes().get(DECORATED_ATTR);
        if (userId == null || out == null) {
            return; // conexión que no se autenticó: ya se cerró
        }
        String frameId = null;
        try {
            JsonNode frame = mapper.readTree(message.getPayload());
            String type = text(frame, "type");
            frameId = text(frame, "id");
            JsonNode payload = frame.path("payload");
            MDC.put("correlationId", frameId != null ? frameId : java.util.UUID.randomUUID().toString());
            switch (type == null ? "" : type) {
                case "message.send" -> onSend(out, userId, frameId, payload);
                case "conversation.read" -> onRead(userId, payload);
                case "sync" -> onSync(out, userId, frameId, payload);
                case "ping" -> reply(out, "pong", frameId, Map.of());
                default -> error(out, frameId, "CHAT_UNKNOWN_TYPE", "Tipo de frame no soportado");
            }
        } catch (JsonProcessingException e) {
            error(out, frameId, "CHAT_BAD_FRAME", "El frame no es JSON válido");
        } catch (DomainException e) {
            error(out, frameId, e.code(), e.getMessage());
        } catch (RuntimeException e) {
            // Nunca se registra el contenido del frame (RNF-CHAT-13).
            log.error("Error procesando un frame de WebSocket del usuario {}", userId, e);
            error(out, frameId, "CHAT_INTERNAL_ERROR", "Error interno");
        } finally {
            MDC.remove("correlationId");
        }
    }

    private void onSend(WebSocketSession out, long userId, String frameId, JsonNode p) {
        var result = send.execute(new SendMessageService.Command(userId, longValue(p, "conversation_id"),
                text(p, "client_message_id"), text(p, "body")));
        Message m = result.message();
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("client_message_id", m.clientMessageId());
        ack.put("conversation_id", m.conversationId());
        ack.put("message_id", m.id());
        ack.put("seq", m.seq());
        ack.put("created_at", m.createdAt().toString());
        ack.put("warnings", result.warnings());
        ack.put("duplicate", result.duplicate());
        reply(out, "message.ack", frameId, ack);
    }

    private void onRead(long userId, JsonNode p) {
        markAsRead.execute(userId, longValue(p, "conversation_id"), longValue(p, "seq"));
        // El aviso al otro lado sale por conversation.read_updated desde el caso de uso.
    }

    private void onSync(WebSocketSession out, long userId, String frameId, JsonNode p) {
        List<SyncMessagesService.Cursor> cursors = new ArrayList<>();
        for (JsonNode c : p.path("conversations")) {
            cursors.add(new SyncMessagesService.Cursor(longValue(c, "conversation_id"), longValue(c, "last_seq")));
        }
        List<Map<String, Object>> conversations = new ArrayList<>();
        for (var result : sync.execute(userId, cursors)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("conversation_id", result.conversationId());
            entry.put("messages", result.messages().stream().map(ChatWebSocketHandler::messageJson).toList());
            entry.put("has_more", result.hasMore());
            conversations.add(entry);
        }
        reply(out, "sync.result", frameId, Map.of("conversations", conversations));
    }

    private static Map<String, Object> messageJson(Message m) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("conversation_id", m.conversationId());
        json.put("message_id", m.id());
        json.put("seq", m.seq());
        json.put("sender_id", m.senderId());
        json.put("kind", m.kind().name());
        json.put("body", m.body());
        json.put("flags", m.flags().stream().map(MessageFlag::name).sorted().toList());
        json.put("created_at", m.createdAt().toString());
        return json;
    }

    private void reply(WebSocketSession out, String type, String refId, Map<String, Object> payload) {
        try {
            out.sendMessage(new TextMessage(Frames.of(mapper, type, refId, payload)));
        } catch (IOException | RuntimeException e) {
            log.debug("No se pudo responder {} a la sesión {}: {}", type, out.getId(), e.toString());
        }
    }

    private void error(WebSocketSession out, String refId, String code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", message);
        payload.put("ref_id", refId);
        reply(out, "error", refId, payload);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() ? v.asText() : null;
    }

    private static long longValue(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (!v.isIntegralNumber()) {
            throw new BadFrameException(field);
        }
        return v.asLong();
    }

    private static final class BadFrameException extends DomainException {
        BadFrameException(String field) {
            super("CHAT_BAD_FRAME", "Falta el campo numérico «" + field + "»");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get(USER_ID_ATTR);
        WebSocketSession decorated = (WebSocketSession) session.getAttributes().get(DECORATED_ATTR);
        if (userId != null && decorated != null) {
            registry.remove(userId, decorated);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Error de transporte en la sesión {}: {}", session.getId(), exception.toString());
    }
}
