package com.parosurvivors.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Enrutado real (no mocks): dos backends HTTP mínimos hacen de monolito y de notifications-service; se
 * comprueba a cuál llega cada ruta y que el gateway resuelve el CORS.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayRoutingTest {

    private static final HttpServer MONOLITH = stub("monolith");
    private static final HttpServer NOTIFICATIONS = stub("notifications-service");

    @Autowired private WebTestClient client;

    private static HttpServer stub(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
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

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("MONOLITH_URI", () -> "http://127.0.0.1:" + MONOLITH.getAddress().getPort());
        registry.add("NOTIFICATIONS_SERVICE_URI", () -> "http://127.0.0.1:" + NOTIFICATIONS.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        MONOLITH.stop(0);
        NOTIFICATIONS.stop(0);
    }

    @Test
    void notificationRoutes_goToNotificationsService() {
        client.get().uri("/api/v1/notifications?read=false").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("notifications-service /api/v1/notifications?read=false");
        client.post().uri("/api/v1/notifications/7/read").exchange()
                .expectBody(String.class).isEqualTo("notifications-service /api/v1/notifications/7/read");
        client.get().uri("/api/v1/notification-channels").exchange()
                .expectBody(String.class).isEqualTo("notifications-service /api/v1/notification-channels");
    }

    @Test
    void everythingElse_goesToTheMonolith() {
        client.get().uri("/api/v1/services?page=0").exchange()
                .expectBody(String.class).isEqualTo("monolith /api/v1/services?page=0");
        client.get().uri("/uploads/services/1/a.jpg").exchange()
                .expectBody(String.class).isEqualTo("monolith /uploads/services/1/a.jpg");
        // Ruta parecida pero de otro recurso: NO debe caer en notifications-service.
        client.get().uri("/api/v1/notificationsXYZ").exchange()
                .expectBody(String.class).isEqualTo("monolith /api/v1/notificationsXYZ");
    }

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
}
