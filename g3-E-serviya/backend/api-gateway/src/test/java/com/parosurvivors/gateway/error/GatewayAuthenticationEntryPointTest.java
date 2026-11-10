package com.parosurvivors.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * Mapea cada fallo de autenticación al {@link GatewayErrorCode} correcto en el cuerpo 4.4 (T3.7). Cubre el
 * caso {@code TOKEN_REVOKED}, que el test e2e no puede provocar sin un {@code min_iat} en Redis.
 */
class GatewayAuthenticationEntryPointTest {

    private final GatewayAuthenticationEntryPoint entryPoint =
            new GatewayAuthenticationEntryPoint(new GatewayErrorWriter(new ObjectMapper(), new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));

    private MockServerWebExchange respondTo(AuthenticationException ex) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/x"));
        entryPoint.commence(exchange, ex).block();
        return exchange;
    }

    private static OAuth2AuthenticationException oauthError(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token", description, null));
    }

    @Test
    void revokedToken_mapsToTokenRevoked() {
        MockServerWebExchange exchange = respondTo(oauthError("El token fue revocado (min_iat)"));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"TOKEN_REVOKED\"")
                .contains("\"status\":401")
                .contains("\"correlationId\":");
    }

    @Test
    void expiredToken_mapsToTokenExpired() {
        MockServerWebExchange exchange = respondTo(oauthError("An error occurred: Jwt expired at 2020-01-01"));
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":\"TOKEN_EXPIRED\"");
    }

    @Test
    void otherOauthError_mapsToTokenInvalid() {
        MockServerWebExchange exchange = respondTo(oauthError("Signature verification failed"));
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":\"TOKEN_INVALID\"");
    }

    @Test
    void noCredentials_mapsToTokenMissing_withBareBearerChallenge() {
        MockServerWebExchange exchange = respondTo(new AuthenticationCredentialsNotFoundException("no auth"));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":\"TOKEN_MISSING\"");
    }
}
