package com.parosurvivors.chat.realtime.infrastructure;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Mapa local {@code user_id → sesiones} de ESTA instancia (guía §11). Mientras un usuario tiene al menos una sesión
 * aquí, la instancia está suscrita a su canal {@code chat:user:{id}} del Pub/Sub propio: así recibe lo que cualquier
 * otra instancia publique para él, sin sesiones pegajosas. Al cerrar la última sesión se desuscribe.
 */
@Component
class ConnectionRegistry implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(ConnectionRegistry.class);

    private final RedisMessageListenerContainer container;
    private final Map<Long, List<WebSocketSession>> sessions = new HashMap<>();

    ConnectionRegistry(RedisMessageListenerContainer container) {
        this.container = container;
    }

    synchronized void add(long userId, WebSocketSession session) {
        List<WebSocketSession> list = sessions.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
        boolean first = list.isEmpty();
        list.add(session);
        if (first) {
            container.addMessageListener(this, new ChannelTopic(RealtimeChannels.forUser(userId)));
        }
    }

    synchronized void remove(long userId, WebSocketSession session) {
        List<WebSocketSession> list = sessions.get(userId);
        if (list == null) {
            return;
        }
        list.remove(session);
        if (list.isEmpty()) {
            sessions.remove(userId);
            container.removeMessageListener(this, new ChannelTopic(RealtimeChannels.forUser(userId)));
        }
    }

    synchronized int connectedUsers() {
        return sessions.size();
    }

    /** Llega del Pub/Sub: el canal identifica al usuario y el cuerpo ya es el frame JSON listo para enviar. */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        long userId = Long.parseLong(channel.substring(channel.lastIndexOf(':') + 1));
        send(userId, new String(message.getBody(), StandardCharsets.UTF_8));
    }

    void send(long userId, String frame) {
        List<WebSocketSession> list;
        synchronized (this) {
            list = sessions.containsKey(userId) ? new ArrayList<>(sessions.get(userId)) : List.of();
        }
        for (WebSocketSession s : list) {
            try {
                if (s.isOpen()) {
                    s.sendMessage(new TextMessage(frame));
                }
            } catch (IOException | RuntimeException e) {
                // Sesión lenta o rota (el decorador lanza si se desborda el buffer): se cierra y el cliente
                // se recupera reconectando y haciendo sync (RNF-CHAT-16).
                log.debug("Cerrando sesión {} de usuario {}: {}", s.getId(), userId, e.toString());
                closeQuietly(s, CloseStatus.SESSION_NOT_RELIABLE);
            }
        }
    }

    /** Ping de protocolo cada ~25 s: mantiene vivas las conexiones a través de proxies y detecta las muertas. */
    @Scheduled(fixedDelayString = "${serviya.chat.ws.heartbeat-ms:25000}")
    void heartbeat() {
        List<WebSocketSession> all;
        synchronized (this) {
            all = sessions.values().stream().flatMap(List::stream).toList();
        }
        for (WebSocketSession s : all) {
            try {
                if (s.isOpen()) {
                    s.sendMessage(new PingMessage());
                }
            } catch (IOException | RuntimeException e) {
                closeQuietly(s, CloseStatus.SESSION_NOT_RELIABLE);
            }
        }
    }

    /** Cierre ordenado (SIGTERM): 1001 para que los clientes reconecten a otra instancia. */
    @PreDestroy
    void closeAll() {
        List<WebSocketSession> all;
        synchronized (this) {
            all = sessions.values().stream().flatMap(List::stream).toList();
        }
        all.forEach(s -> closeQuietly(s, CloseStatus.GOING_AWAY));
    }

    private static void closeQuietly(WebSocketSession s, CloseStatus status) {
        try {
            s.close(status);
        } catch (IOException | RuntimeException ignored) {
            // ya estaba cerrada
        }
    }
}
