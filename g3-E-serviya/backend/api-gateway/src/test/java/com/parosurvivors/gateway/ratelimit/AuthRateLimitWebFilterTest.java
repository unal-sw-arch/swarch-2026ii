package com.parosurvivors.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parosurvivors.gateway.error.GatewayErrorWriter;
import com.parosurvivors.gateway.ratelimit.GatewayRateLimitProperties.SlidingPolicy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
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

/** Cuota de auth por IP en el borde: aplica la política por ruta, corta con 429 y no toca lo no-auth. */
@ExtendWith(MockitoExtension.class)
class AuthRateLimitWebFilterTest {

    @Mock private ReactiveRedisRateLimiter rateLimiter;

    private GatewayRateLimitProperties properties;

    @BeforeEach
    void setUp() {
        SlidingPolicy loginIp = new SlidingPolicy();
        loginIp.setCapacity(10);
        loginIp.setWindow(Duration.ofMinutes(15));
        loginIp.setFailOpen(true);
        properties = new GatewayRateLimitProperties();
        properties.setAuth(Map.of("login-ip", loginIp));
    }

    private AuthRateLimitWebFilter filter() {
        return new AuthRateLimitWebFilter(rateLimiter, properties, new GatewayErrorWriter(new ObjectMapper(), new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    @Test
    void appliesLoginPolicy_andAllows() {
        when(rateLimiter.tryConsumeSliding(eq("login-ip"), eq("ip"), anyString(), anyLong(), anyLong(), anyBoolean()))
                .thenReturn(Mono.just(RateLimitDecision.allow()));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login"));
        AtomicBoolean chained = new AtomicBoolean(false);
        WebFilterChain chain = e -> {
            chained.set(true);
            return Mono.empty();
        };

        filter().filter(exchange, chain).block();

        assertThat(chained).isTrue();
        verify(rateLimiter).tryConsumeSliding(eq("login-ip"), eq("ip"), anyString(), eq(10L), eq(900_000L), eq(true));
    }

    @Test
    void rejectsWith429_whenQuotaExceeded() {
        when(rateLimiter.tryConsumeSliding(anyString(), anyString(), anyString(), anyLong(), anyLong(), anyBoolean()))
                .thenReturn(Mono.just(RateLimitDecision.rejected(42)));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login"));
        AtomicBoolean chained = new AtomicBoolean(false);
        WebFilterChain chain = e -> {
            chained.set(true);
            return Mono.empty();
        };

        filter().filter(exchange, chain).block();

        assertThat(chained).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":\"RATE_LIMITED\"");
    }

    @Test
    void skipsNonAuthLimitedPaths() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/services"));
        AtomicBoolean chained = new AtomicBoolean(false);
        WebFilterChain chain = e -> {
            chained.set(true);
            return Mono.empty();
        };

        filter().filter(exchange, chain).block();

        assertThat(chained).isTrue();
        verifyNoInteractions(rateLimiter);
    }
}
