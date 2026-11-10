package com.parosurvivors.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * El gateway debe reenviar el upgrade WebSocket de {@code /api/chat/v1/ws} al chat, con su query string (el ticket
 * de un solo uso viaja ahí) y conservando {@code Origin} (el chat lo valida en el handshake). Un servidor WebSocket mínimo hace de chat-service y responde con eco.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayWebSocketTest {

    /** Eco que antepone la URI recibida: así se comprueba que el ticket de la query llega al chat. */
    private static final DisposableServer CHAT = HttpServer.create().host("127.0.0.1").port(0)
            .handle((req, res) -> res.sendWebsocket((in, out) ->
                    out.sendString(in.receive().asString().map(text -> "echo " + req.uri()
                            + " origin=" + req.requestHeaders().get("Origin") + " " + text))))
            .bindNow();

    @LocalServerPort int port;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("CHAT_SERVICE_URI", () -> "http://127.0.0.1:" + CHAT.port());
        registry.add("CHAT_WS_URI", () -> "ws://127.0.0.1:" + CHAT.port());
    }

    @AfterAll
    static void stop() {
        CHAT.disposeNow();
    }

    @Test
    void el_upgrade_websocket_del_chat_atraviesa_el_gateway_en_ambos_sentidos() {
        List<String> received = new ArrayList<>();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setOrigin("http://localhost:5173");
        new ReactorNettyWebSocketClient().execute(
                URI.create("ws://localhost:" + port + "/api/chat/v1/ws?ticket=abc123"), headers,
                session -> session.send(Mono.just(session.textMessage("hola")))
                        .thenMany(session.receive().map(WebSocketMessage::getPayloadAsText).take(1).doOnNext(received::add))
                        .then())
                .block(Duration.ofSeconds(10));

        assertThat(received).containsExactly("echo /api/chat/v1/ws?ticket=abc123 origin=http://localhost:5173 hola");
    }
}
