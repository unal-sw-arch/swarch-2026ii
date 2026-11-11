package com.parosurvivors.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

/** El cuerpo de error del borde incrementa la métrica T6.2 por estado y código. */
class GatewayErrorWriterTest {

    @Test
    void incrementsAuthErrorCounter_byStatusAndCode() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayErrorWriter writer = new GatewayErrorWriter(new ObjectMapper(), registry);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/x"));

        writer.write(exchange, HttpStatus.UNAUTHORIZED, GatewayErrorCode.TOKEN_EXPIRED, "expirado", "Bearer").block();

        assertThat(registry.get("gateway.auth.error")
                .tag("status", "401").tag("code", "TOKEN_EXPIRED").counter().count()).isEqualTo(1.0);
    }
}
