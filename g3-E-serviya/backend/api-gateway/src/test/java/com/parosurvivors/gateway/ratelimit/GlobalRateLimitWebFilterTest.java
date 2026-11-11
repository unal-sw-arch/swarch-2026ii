package com.parosurvivors.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parosurvivors.gateway.error.GatewayErrorWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Techo global (T3.9): deja pasar si hay cuota, corta con 429 + Retry-After si no, y no toca lo no-API. */
@ExtendWith(MockitoExtension.class)
class GlobalRateLimitWebFilterTest {

    @Mock private ReactiveRedisRateLimiter rateLimiter;

    private GlobalRateLimitWebFilter filter() {
        return new GlobalRateLimitWebFilter(rateLimiter, new GatewayErrorWriter(new ObjectMapper(), new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    @Test
    void allowsRequest_whenWithinQuota() {
        when(rateLimiter.tryConsume(anyString(), anyString())).thenReturn(Mono.just(RateLimitDecision.allow()));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/services"));
        AtomicBoolean chained = new AtomicBoolean(false);
        WebFilterChain chain = e -> {
            chained.set(true);
            return Mono.empty();
        };

        filter().filter(exchange, chain).block();

        assertThat(chained).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void rejectsWith429_andRetryAfter_whenQuotaExceeded() {
        when(rateLimiter.tryConsume(anyString(), anyString())).thenReturn(Mono.just(RateLimitDecision.rejected(7)));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/services"));
        AtomicBoolean chained = new AtomicBoolean(false);
        WebFilterChain chain = e -> {
            chained.set(true);
            return Mono.empty();
        };

        filter().filter(exchange, chain).block();

        assertThat(chained).isFalse(); // cortado antes de enrutar
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("7");
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":\"RATE_LIMITED\"");
    }

    @Test
    void skipsNonApiPaths() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/gateway-actuator/health"));
        AtomicBoolean chained = new AtomicBoolean(false);
        WebFilterChain chain = e -> {
            chained.set(true);
            return Mono.empty();
        };

        filter().filter(exchange, chain).block();

        assertThat(chained).isTrue();
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void anonymousRequest_usesIpSubject() {
        when(rateLimiter.tryConsume(anyString(), anyString())).thenReturn(Mono.just(RateLimitDecision.allow()));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/services"));

        filter().filter(exchange, e -> Mono.empty()).block();

        // Sin contexto de seguridad ⇒ sujeto por IP (no por usuario).
        verify(rateLimiter).tryConsume(eq("ip"), anyString());
    }
}
