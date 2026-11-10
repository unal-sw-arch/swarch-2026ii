package com.parosurvivors.chat.realtime.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {

    static final String PATH = "/api/chat/v1/ws";
    /** Tamaño máximo de un frame entrante (8 KB): evita abusos; al superarlo el contenedor cierra con 1009. */
    static final int MAX_FRAME_BYTES = 8 * 1024;

    private final ChatWebSocketHandler handler;
    private final String[] allowedOrigins;

    WebSocketConfig(ChatWebSocketHandler handler, @Value("${serviya.chat.allowed-ws-origins}") String[] allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // setAllowedOrigins valida la cabecera Origin del handshake (403 si no está en la lista).
        registry.addHandler(handler, PATH).setAllowedOrigins(allowedOrigins);
    }

    @Bean
    ServletServerContainerFactoryBean webSocketContainer(
            @Value("${serviya.chat.ws.idle-timeout-ms:90000}") long idleTimeoutMs) {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_FRAME_BYTES);
        container.setMaxBinaryMessageBufferSize(MAX_FRAME_BYTES);
        // Mayor que el heartbeat (25 s) y que el timeout del gateway (≥ 60 s).
        container.setMaxSessionIdleTimeout(idleTimeoutMs);
        return container;
    }
}
