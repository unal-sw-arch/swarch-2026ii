package com.parosurvivors.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.parosurvivors.gateway.security.TestJwks;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Enrutado + seguridad del gateway (Fase 3), de extremo a extremo y sin mocks de Spring: dos backends HTTP
 * mínimos hacen de monolito y de notifications-service, y un {@link MockWebServer} publica el JWKS con el
 * que el gateway valida los access tokens ES256 ({@link TestJwks} los firma). Se comprueba a qué backend
 * llega cada ruta, el CORS del borde, y la matriz de autorización (público / autenticado / ADMIN).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayRoutingTest {

    private static final String ISSUER = "https://auth.serviya.local";
    private static final String AUDIENCE = "marketplace-api";

    private static final HttpServer MONOLITH = stub("monolith");
    private static final HttpServer NOTIFICATIONS = stub("notifications-service");
    private static final HttpServer CHAT = stub("chat-service");
    private static final TestJwks JWKS = new TestJwks();
    private static final MockWebServer JWKS_SERVER = jwksServer();

    private static final String CLIENT_TOKEN = JWKS.validToken(ISSUER, AUDIENCE, "1", List.of("CLIENT"));
    private static final String ADMIN_TOKEN = JWKS.validToken(ISSUER, AUDIENCE, "2", List.of("ADMIN"));

    // Lo que los backends recibieron del gateway (para verificar passthrough de Authorization y correlation id).
    private static final AtomicReference<String> LAST_FORWARDED_AUTH = new AtomicReference<>();
    private static final AtomicReference<String> LAST_FORWARDED_CORRELATION = new AtomicReference<>();

    @Autowired private WebTestClient client;

    private static HttpServer stub(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                LAST_FORWARDED_AUTH.set(exchange.getRequestHeaders().getFirst("Authorization"));
                LAST_FORWARDED_CORRELATION.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
                String origin = exchange.getRequestHeaders().getFirst("Origin");
                byte[] body = (name + " " + exchange.getRequestURI() + (origin == null ? "" : " origin=" + origin))
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static MockWebServer jwksServer() {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .setBody(JWKS.jwksJson());
            }
        });
        try {
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("MONOLITH_URI", () -> "http://127.0.0.1:" + MONOLITH.getAddress().getPort());
        registry.add("NOTIFICATIONS_SERVICE_URI", () -> "http://127.0.0.1:" + NOTIFICATIONS.getAddress().getPort());
        registry.add("CHAT_SERVICE_URI", () -> "http://127.0.0.1:" + CHAT.getAddress().getPort());
        registry.add("JWT_JWKS_URI", () -> JWKS_SERVER.url("/.well-known/jwks.json").toString());
        registry.add("JWT_ISSUER", () -> ISSUER);
        registry.add("JWT_AUDIENCE", () -> AUDIENCE);
    }

    @AfterAll
    static void stop() throws IOException {
        MONOLITH.stop(0);
        NOTIFICATIONS.stop(0);
        CHAT.stop(0);
        JWKS_SERVER.shutdown();
    }

    // --- Enrutado (con token válido donde la ruta ya no es pública) ---

    @Test
    void notificationRoutes_goToNotificationsService() {
        client.get().uri("/api/v1/notifications?read=false").headers(bearer(CLIENT_TOKEN)).exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("notifications-service /api/v1/notifications?read=false");
        client.post().uri("/api/v1/notifications/7/read").headers(bearer(CLIENT_TOKEN)).exchange()
                .expectBody(String.class).isEqualTo("notifications-service /api/v1/notifications/7/read");
        client.get().uri("/api/v1/notification-channels").headers(bearer(CLIENT_TOKEN)).exchange()
                .expectBody(String.class).isEqualTo("notifications-service /api/v1/notification-channels");
    }

    @Test
    void chatRoutes_goToChatService() {
        // El REST del chat está autenticado en el gateway (Bearer), como notif: así aplica min_iat. El
        // ticket WS se pide por REST autenticado y luego el handshake WS (permitAll) lo valida el chat.
        client.get().uri("/api/chat/v1/conversations?status=ACTIVE").headers(bearer(CLIENT_TOKEN)).exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("chat-service /api/chat/v1/conversations?status=ACTIVE");
        client.post().uri("/api/chat/v1/ws-tickets").headers(bearer(CLIENT_TOKEN)).exchange()
                .expectBody(String.class).isEqualTo("chat-service /api/chat/v1/ws-tickets");
        // Prefijo parecido pero de otro recurso: NO debe caer en chat-service.
        client.get().uri("/api/chatter").headers(bearer(CLIENT_TOKEN)).exchange()
                .expectBody(String.class).isEqualTo("monolith /api/chatter");
    }

    @Test
    void everythingElse_goesToTheMonolith() {
        // Ruta pública: catálogo de solo-lectura, sin token.
        client.get().uri("/api/v1/services?page=0").exchange()
                .expectBody(String.class).isEqualTo("monolith /api/v1/services?page=0");
        // Rutas no públicas: requieren token.
        client.get().uri("/uploads/services/1/a.jpg").headers(bearer(CLIENT_TOKEN)).exchange()
                .expectBody(String.class).isEqualTo("monolith /uploads/services/1/a.jpg");
        client.get().uri("/api/v1/notificationsXYZ").headers(bearer(CLIENT_TOKEN)).exchange()
                .expectBody(String.class).isEqualTo("monolith /api/v1/notificationsXYZ");
    }

    // --- Seguridad: validación del token y matriz de autorización ---

    @Test
    void protectedRoute_withoutToken_is401_withTokenMissingBody() {
        client.get().uri("/api/v1/notifications").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("unauthorized")
                .jsonPath("$.code").isEqualTo("TOKEN_MISSING")
                .jsonPath("$.correlationId").exists();
    }

    @Test
    void tokenWithForbiddenAlgorithm_is401() {
        client.get().uri("/api/v1/notifications")
                .headers(bearer(JWKS.hs256Token(ISSUER, AUDIENCE, "1")))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void expiredToken_is401_withTokenExpiredCode() {
        client.get().uri("/api/v1/notifications")
                .headers(bearer(JWKS.expiredToken(ISSUER, AUDIENCE, "1", List.of("CLIENT"))))
                .exchange().expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void tokenForAnotherAudience_is401_withTokenInvalidCode() {
        client.get().uri("/api/v1/notifications")
                .headers(bearer(JWKS.validToken(ISSUER, "otra-api", "1", List.of("CLIENT"))))
                .exchange().expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("TOKEN_INVALID");
    }

    @Test
    void tokenFromAnotherIssuer_is401() {
        client.get().uri("/api/v1/notifications")
                .headers(bearer(JWKS.validToken("https://evil.example", AUDIENCE, "1", List.of("CLIENT"))))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void adminRoute_withClientRole_is403_withForbiddenCode() {
        client.get().uri("/api/v1/reports").headers(bearer(CLIENT_TOKEN))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void adminRoute_withAdminRole_routesToMonolith() {
        client.get().uri("/api/v1/reports").headers(bearer(ADMIN_TOKEN))
                .exchange().expectStatus().isOk()
                .expectBody(String.class).isEqualTo("monolith /api/v1/reports");
    }

    @Test
    void publicRoute_withoutToken_routes() {
        client.get().uri("/api/v1/categories").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("monolith /api/v1/categories");
    }

    // --- Correlation ID (T3.10) y passthrough de Authorization (T3.11) ---

    @Test
    void authorizationHeader_isForwardedIntactToBackends() {
        client.get().uri("/api/v1/notifications").headers(bearer(CLIENT_TOKEN)).exchange().expectStatus().isOk();
        assertThat(LAST_FORWARDED_AUTH.get()).isEqualTo("Bearer " + CLIENT_TOKEN);
    }

    @Test
    void correlationId_isGenerated_returned_andForwarded() {
        client.get().uri("/api/v1/categories").exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id");
        assertThat(LAST_FORWARDED_CORRELATION.get()).isNotBlank();
    }

    @Test
    void correlationId_isPreserved_whenProvidedByClient() {
        client.get().uri("/api/v1/categories").header("X-Request-Id", "trace-abc-123").exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "trace-abc-123");
        assertThat(LAST_FORWARDED_CORRELATION.get()).isEqualTo("trace-abc-123");
    }

    // --- CORS en el borde ---

    @Test
    void corsIsResolvedAtTheEdge_originIsNotForwardedToBackends() {
        client.get().uri("/api/v1/services")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                .expectBody(String.class).isEqualTo("monolith /api/v1/services");
    }

    @Test
    void cors_disallowedOrigin_getsNoAllowOriginHeader() {
        // Origen fuera de la allowlist: la petición simple se procesa, pero SIN cabecera de permiso CORS
        // (el navegador bloquea la respuesta). Así se verifica que la allowlist por entorno aplica (T3.8).
        client.get().uri("/api/v1/services")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .exchange()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void corsPreflight_isAnsweredByTheGateway_forAllowedOrigins() {
        client.options().uri("/api/v1/notifications")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");

        client.options().uri("/api/v1/notifications")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isForbidden();
    }

    private static java.util.function.Consumer<HttpHeaders> bearer(String token) {
        return headers -> headers.setBearerAuth(token);
    }
}
