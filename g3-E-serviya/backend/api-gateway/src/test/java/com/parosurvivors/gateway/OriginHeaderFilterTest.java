package com.parosurvivors.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class OriginHeaderFilterTest {

    private final OriginHeaderFilter filter = new OriginHeaderFilter();

    @Test
    void removesOriginForRegularRoutes() {
        ServerWebExchange exchange = exchange("/api/v1/services", "http://localhost:5173");
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, filtered -> {
            forwarded.set(filtered);
            return Mono.empty();
        }).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst(HttpHeaders.ORIGIN)).isNull();
    }

    @Test
    void preservesOriginForChatWebSocket() {
        ServerWebExchange exchange = exchange("/api/chat/v1/ws", "http://localhost:5173");
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, filtered -> {
            forwarded.set(filtered);
            return Mono.empty();
        }).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst(HttpHeaders.ORIGIN))
                .isEqualTo("http://localhost:5173");
    }

    private ServerWebExchange exchange(String path, String origin) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path)
                .header(HttpHeaders.ORIGIN, origin)
                .build());
    }
}
