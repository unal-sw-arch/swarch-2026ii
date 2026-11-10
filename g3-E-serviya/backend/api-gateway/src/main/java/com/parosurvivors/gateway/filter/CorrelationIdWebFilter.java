package com.parosurvivors.gateway.filter;

import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Correlation ID del gateway (T3.10): garantiza un {@code X-Correlation-Id} por petición, lo propaga a los
 * servicios de detrás (reescribe la cabecera de la request, que el gateway reenvía) y lo devuelve en la
 * respuesta. Si el cliente ya envió uno, se respeta (traza extremo a extremo); si no, se genera.
 *
 * <p>Corre con la <b>máxima precedencia</b>, antes de la cadena de Spring Security, para que el
 * {@code GatewayErrorWriter} (401/403) lea el mismo id desde la request y el cuerpo de error quede
 * correlacionado con los logs de los servicios.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    // Unificado con el chat y el envelope de eventos: X-Request-Id (no X-Correlation-Id).
    public static final String CORRELATION_ID_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        String correlationId = (incoming != null && !incoming.isBlank())
                ? incoming
                : UUID.randomUUID().toString();

        ServerWebExchange mutated = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(CORRELATION_ID_HEADER, correlationId)))
                .build();
        // beforeCommit: asegura la cabecera en la respuesta aunque el proxy escriba las de los servicios.
        mutated.getResponse().beforeCommit(() -> {
            mutated.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);
            return Mono.empty();
        });
        // Disponible en el contexto reactivo para logs correlacionados dentro del gateway.
        return chain.filter(mutated)
                .contextWrite(context -> context.put(CORRELATION_ID_HEADER, correlationId));
    }
}
