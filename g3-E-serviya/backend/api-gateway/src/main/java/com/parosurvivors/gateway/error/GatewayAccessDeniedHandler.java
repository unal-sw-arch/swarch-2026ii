package com.parosurvivors.gateway.error;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Handler 403 del gateway (T3.7): autenticado pero sin el rol necesario → cuerpo 4.4 con
 * {@link GatewayErrorCode#FORBIDDEN}. El cliente <b>no</b> debe refrescar el token (decisión #12).
 */
public class GatewayAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final GatewayErrorWriter writer;

    public GatewayAccessDeniedHandler(GatewayErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return writer.write(exchange, HttpStatus.FORBIDDEN, GatewayErrorCode.FORBIDDEN,
                "No tienes permiso para acceder a este recurso.", null);
    }
}
