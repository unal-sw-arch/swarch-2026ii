package com.parosurvivors.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * El gateway es la autoridad CORS: para las rutas normales retira {@code Origin} antes de
 * reenviar, para que el servicio de atrás no duplique cabeceras CORS.
 *
 * <p>Excepción: el WebSocket del chat valida el origen del handshake, así que en
 * {@code /api/chat/v1/ws} el header debe conservarse.</p>
 *
 * <p>Sustituye al antiguo default-filter {@code RemoveRequestHeader=Origin}, que no permitía
 * excluir una ruta.</p>
 */
@Component
public class OriginHeaderFilter implements GlobalFilter, Ordered {

    static final String CHAT_WEBSOCKET_PATH = "/api/chat/v1/ws";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.equals(CHAT_WEBSOCKET_PATH) || path.startsWith(CHAT_WEBSOCKET_PATH + "/")) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.remove(HttpHeaders.ORIGIN))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
