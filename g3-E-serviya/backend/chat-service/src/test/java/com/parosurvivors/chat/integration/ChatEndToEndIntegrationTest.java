package com.parosurvivors.chat.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.parosurvivors.chat.shared.application.ports.OnlineUsers;
import com.parosurvivors.chat.shared.infrastructure.outbox.OutboxBatchPublisher;
import com.parosurvivors.chat.support.WsTestClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * El MVP de punta a punta contra infraestructura real: un productor de prueba hace de monolito publicando los
 * eventos de solicitudes y usuarios, y dos clientes WebSocket conversan por el chat. Cubre los criterios de
 * aceptación del MVP (guía §3.3) y la seguridad (RNF-CHAT-10/11).
 */
class ChatEndToEndIntegrationTest extends IntegrationTestBase {

    private static final long CLIENT = 1001;
    private static final long OFFERER = 1002;
    private static final long STRANGER = 1003;
    private static final AtomicLong REQUEST_IDS = new AtomicLong(5000);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    /** Admin: hace de monolito (publica en serviya.requests/users) e inspecciona colas; el chat corre como `chat`. */
    private final RabbitTemplate amqp = adminRabbitTemplate();
    private final AmqpAdmin amqpAdmin = adminAmqp();
    @Autowired OnlineUsers onlineUsers;
    /** El RabbitTemplate de la APLICACIÓN: conecta como el usuario `chat` con los permisos de infra. */
    @Autowired RabbitTemplate chatRabbit;
    @Autowired OutboxBatchPublisher outbox;
    @Value("${serviya.chat.allowed-ws-origins}") String allowedOrigin;

    private final List<WsTestClient> clients = new ArrayList<>();
    private String capturedQueue;

    @BeforeEach
    void setUp() {
        amqpAdmin.purgeQueue("chat-service.requests.dlq", false);
        amqpAdmin.purgeQueue("chat-service.users.dlq", false);
        // Cola temporal que captura lo que el chat publica hacia otros servicios (Métricas, Notificaciones).
        capturedQueue = "test-captured-" + UUID.randomUUID();
        amqpAdmin.declareQueue(new Queue(capturedQueue, true, false, false));
        Binding chat = BindingBuilder.bind(new Queue(capturedQueue)).to(new TopicExchange("serviya.chat")).with("chat.#");
        Binding notifications = BindingBuilder.bind(new Queue(capturedQueue)).to(new TopicExchange("serviya.notifications"))
                .with("notification.requested.v1");
        amqpAdmin.declareBinding(chat);
        amqpAdmin.declareBinding(notifications);
    }

    @AfterEach
    void tearDown() {
        clients.forEach(WsTestClient::close);
        amqpAdmin.deleteQueue(capturedQueue);
    }

    // ================= helpers =================

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) {
        return http.exchange(url(path), method, new HttpEntity<>(body, token == null ? new HttpHeaders() : auth(token)),
                JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String path, long userId) {
        return call(HttpMethod.GET, path, token(userId), null);
    }

    private void publishEvent(String exchange, String type, String eventId, Instant at, Map<String, Object> data) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("event_id", eventId);
            envelope.put("event_type", type);
            envelope.put("event_version", 1);
            envelope.put("occurred_at", at.toString());
            envelope.put("producer", "stub-monolith");
            envelope.put("correlation_id", UUID.randomUUID().toString());
            envelope.put("data", data);
            amqp.send(exchange, type, MessageBuilder.withBody(MAPPER.writeValueAsBytes(envelope))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON).build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** El productor de prueba publica {@code requests.service_request.created} y espera a que el chat abra la conversación. */
    private long openConversation(long requestId, long client, long offerer) {
        publishEvent("serviya.requests", "requests.service_request.created", UUID.randomUUID().toString(), Instant.now(),
                Map.of("request_id", requestId, "client_id", client, "offerer_id", offerer, "status", "PENDING"));
        await().atMost(Duration.ofSeconds(15)).until(() ->
                get("/api/chat/v1/conversations/by-request/" + requestId, client).getStatusCode() == HttpStatus.OK);
        return get("/api/chat/v1/conversations/by-request/" + requestId, client).getBody().path("id").asLong();
    }

    private WsTestClient connectWs(long userId) {
        ResponseEntity<JsonNode> ticket = call(HttpMethod.POST, "/api/chat/v1/ws-tickets", token(userId), null);
        assertThat(ticket.getStatusCode()).isEqualTo(HttpStatus.OK);
        WsTestClient client = WsTestClient.connect("ws://localhost:" + port + "/api/chat/v1/ws?ticket="
                + ticket.getBody().path("ticket").asText());
        clients.add(client);
        // La instancia se suscribe al canal del usuario de forma asíncrona: se espera a que cuente como «en línea».
        await().atMost(Duration.ofSeconds(10)).until(() -> onlineUsers.onlineAmong(List.of(userId)).contains(userId));
        return client;
    }

    private Map<String, Object> send(long conversationId, String clientMessageId, String body) {
        return Map.of("conversation_id", conversationId, "client_message_id", clientMessageId, "body", body);
    }

    /** Publica el outbox y devuelve lo capturado por la cola temporal. */
    private List<JsonNode> relayAndCapture() throws Exception {
        while (outbox.publishPendingBatch() > 0) {
            // vacía todo
        }
        List<JsonNode> out = new ArrayList<>();
        Message m;
        while ((m = amqp.receive(capturedQueue, 1500)) != null) {
            out.add(MAPPER.readTree(m.getBody()));
        }
        return out;
    }

    // ================= seguridad REST (RNF-CHAT-10/11) =================

    @Test
    void sin_token_o_con_token_invalido_se_responde_401() throws Exception {
        ECKey otherKey = new ECKeyGenerator(Curve.P_256).keyID("test-key-1").generate(); // mismo kid, otra clave
        Instant future = Instant.now().plusSeconds(600);

        assertThat(call(HttpMethod.GET, "/api/chat/v1/conversations", null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET, "/api/chat/v1/conversations", "garbage.token.here", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        // firmado con otra clave
        assertThat(call(HttpMethod.GET, "/api/chat/v1/conversations",
                tokenWith(otherKey, CLIENT, ISSUER, List.of(AUDIENCE), future, "at+jwt"), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        // emisor, audiencia, vencimiento y typ incorrectos
        assertThat(call(HttpMethod.GET, "/api/chat/v1/conversations",
                tokenWith(signingKey, CLIENT, "https://otro", List.of(AUDIENCE), future, "at+jwt"), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET, "/api/chat/v1/conversations",
                tokenWith(signingKey, CLIENT, ISSUER, List.of("otra-api"), future, "at+jwt"), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET, "/api/chat/v1/conversations",
                tokenWith(signingKey, CLIENT, ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(600), "at+jwt"), null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET, "/api/chat/v1/conversations",
                tokenWith(signingKey, CLIENT, ISSUER, List.of(AUDIENCE), future, "JWT"), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        // y el válido sí entra
        assertThat(get("/api/chat/v1/conversations", CLIENT).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void el_health_es_publico_y_el_resto_exige_autenticacion() {
        assertThat(http.getForEntity(url("/actuator/health"), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity(url("/actuator/info"), String.class).getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.POST, "/api/chat/v1/ws-tickets", null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ================= criterio 1: la conversación aparece sola =================

    @Test
    void al_crearse_la_solicitud_cliente_y_oferente_ven_la_conversacion_sin_accion_manual() {
        long requestId = REQUEST_IDS.incrementAndGet();
        long id = openConversation(requestId, CLIENT, OFFERER);

        for (long user : new long[] {CLIENT, OFFERER}) {
            JsonNode list = get("/api/chat/v1/conversations", user).getBody();
            assertThat(list.path("items")).hasSize(1);
            JsonNode item = list.path("items").get(0);
            assertThat(item.path("id").asLong()).isEqualTo(id);
            assertThat(item.path("context_id").asLong()).isEqualTo(requestId);
            assertThat(item.path("status").asText()).isEqualTo("ACTIVE");
            assertThat(item.path("context_status").asText()).isEqualTo("PENDING");
            assertThat(item.path("unread_count").asInt()).isZero();
        }
        assertThat(get("/api/chat/v1/conversations", STRANGER).getBody().path("items")).isEmpty();
    }

    // ================= criterios 2 y 3: tiempo real, orden, lectura =================

    @Test
    void un_mensaje_llega_en_tiempo_real_al_otro_participante_con_ack_al_emisor_y_confirmacion_de_lectura() {
        long id = openConversation(REQUEST_IDS.incrementAndGet(), CLIENT, OFFERER);
        WsTestClient client = connectWs(CLIENT);
        WsTestClient offerer = connectWs(OFFERER);

        long t0 = System.nanoTime();
        client.send("message.send", "f-1", send(id, "cid-1", "Hola, ¿puedes venir mañana?"));

        JsonNode ack = client.await("message.ack", Duration.ofSeconds(10));
        JsonNode delivered = offerer.await("message.new", Duration.ofSeconds(10));
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertThat(ack.path("id").asText()).isEqualTo("f-1");
        assertThat(ack.path("payload").path("client_message_id").asText()).isEqualTo("cid-1");
        assertThat(ack.path("payload").path("seq").asLong()).isEqualTo(1);
        assertThat(ack.path("payload").path("warnings")).isEmpty();
        assertThat(delivered.path("payload").path("body").asText()).isEqualTo("Hola, ¿puedes venir mañana?");
        assertThat(delivered.path("payload").path("sender_id").asLong()).isEqualTo(CLIENT);
        assertThat(delivered.path("payload").path("seq").asLong()).isEqualTo(1);
        assertThat(elapsedMs).as("entrega de punta a punta (criterio 2: < 1 s)").isLessThan(1000);

        // el oferente lo ve como no leído y luego lo marca leído → el cliente recibe la confirmación
        assertThat(get("/api/chat/v1/conversations", OFFERER).getBody().path("items").get(0).path("unread_count").asInt()).isEqualTo(1);
        offerer.send("conversation.read", null, Map.of("conversation_id", id, "seq", 1));
        JsonNode read = client.await("conversation.read_updated", Duration.ofSeconds(10));
        assertThat(read.path("payload").path("user_id").asLong()).isEqualTo(OFFERER);
        assertThat(read.path("payload").path("seq").asLong()).isEqualTo(1);
        assertThat(get("/api/chat/v1/conversations", OFFERER).getBody().path("items").get(0).path("unread_count").asInt()).isZero();
    }

    @Test
    void el_orden_es_el_mismo_para_ambos_y_el_historial_pagina_hacia_atras() {
        long id = openConversation(REQUEST_IDS.incrementAndGet(), CLIENT, OFFERER);
        WsTestClient client = connectWs(CLIENT);
        WsTestClient offerer = connectWs(OFFERER);

        for (int i = 1; i <= 5; i++) {
            WsTestClient from = i % 2 == 1 ? client : offerer;
            from.send("message.send", "f-" + i, send(id, "cid-" + i, "mensaje " + i));
            from.await("message.ack", Duration.ofSeconds(10));
        }

        for (long user : new long[] {CLIENT, OFFERER}) {
            JsonNode page = get("/api/chat/v1/conversations/" + id + "/messages?limit=3", user).getBody();
            assertThat(page.path("items")).extracting(n -> n.path("seq").asLong()).containsExactly(3L, 4L, 5L);
            JsonNode older = get("/api/chat/v1/conversations/" + id + "/messages?before_seq=3&limit=3", user).getBody();
            assertThat(older.path("items")).extracting(n -> n.path("body").asText()).containsExactly("mensaje 1", "mensaje 2");
        }
    }

    @Test
    void al_reconectar_el_sync_devuelve_los_mensajes_perdidos_en_orden_criterio_3() {
        long id = openConversation(REQUEST_IDS.incrementAndGet(), CLIENT, OFFERER);
        WsTestClient client = connectWs(CLIENT);
        client.send("message.send", "f-1", send(id, "cid-1", "uno"));
        client.await("message.ack", Duration.ofSeconds(10));
        // el oferente estaba desconectado: no recibió nada
        client.send("message.send", "f-2", send(id, "cid-2", "dos"));
        client.await("message.ack", Duration.ofSeconds(10));

        WsTestClient offerer = connectWs(OFFERER);
        offerer.send("sync", "s-1", Map.of("conversations", List.of(Map.of("conversation_id", id, "last_seq", 0))));
        JsonNode result = offerer.await("sync.result", Duration.ofSeconds(10));

        JsonNode messages = result.path("payload").path("conversations").get(0).path("messages");
        assertThat(messages).extracting(n -> n.path("body").asText()).containsExactly("uno", "dos");
        assertThat(messages).extracting(n -> n.path("seq").asLong()).containsExactly(1L, 2L);
        assertThat(result.path("payload").path("conversations").get(0).path("has_more").asBoolean()).isFalse();
    }

    // ================= criterio 4: idempotencia =================

    @Test
    void reenviar_con_el_mismo_client_message_id_por_ws_y_por_rest_no_crea_duplicados() {
        long id = openConversation(REQUEST_IDS.incrementAndGet(), CLIENT, OFFERER);
        WsTestClient client = connectWs(CLIENT);
        WsTestClient offerer = connectWs(OFFERER);

        client.send("message.send", "f-1", send(id, "same-id", "Hola"));
        JsonNode first = client.await("message.ack", Duration.ofSeconds(10));
        offerer.await("message.new", Duration.ofSeconds(10));
        client.send("message.send", "f-2", send(id, "same-id", "Hola"));      // reintento por corte de red
        JsonNode retry = client.await("message.ack", Duration.ofSeconds(10));
        ResponseEntity<JsonNode> viaRest = call(HttpMethod.POST, "/api/chat/v1/conversations/" + id + "/messages",
                token(CLIENT), Map.of("client_message_id", "same-id", "body", "Hola"));

        assertThat(retry.path("payload").path("message_id").asLong()).isEqualTo(first.path("payload").path("message_id").asLong());
        assertThat(retry.path("payload").path("duplicate").asBoolean()).isTrue();
        assertThat(viaRest.getStatusCode()).isEqualTo(HttpStatus.OK); // 200 (no 201): reenvío reconocido
        assertThat(viaRest.getBody().path("duplicate").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE conversation_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(offerer.noFrame("message.new", Duration.ofSeconds(1))).as("no se vuelve a entregar").isTrue();
    }

    // ================= criterio 5: terceros =================

    @Test
    void un_tercero_autenticado_no_puede_leer_ni_escribir_ni_marcar_leido() {
        long id = openConversation(REQUEST_IDS.incrementAndGet(), CLIENT, OFFERER);
        WsTestClient stranger = connectWs(STRANGER);

        assertThat(get("/api/chat/v1/conversations/" + id, STRANGER).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/chat/v1/conversations/" + id + "/messages", STRANGER).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<JsonNode> write = call(HttpMethod.POST, "/api/chat/v1/conversations/" + id + "/messages",
                token(STRANGER), Map.of("client_message_id", "x", "body", "intruso"));
        assertThat(write.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(write.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(write.getBody().path("code").asText()).isEqualTo("CHAT_NOT_PARTICIPANT");
        assertThat(call(HttpMethod.POST, "/api/chat/v1/conversations/" + id + "/read", token(STRANGER), Map.of("seq", 1))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        stranger.send("message.send", "f-1", send(id, "x", "intruso"));
        JsonNode error = stranger.await("error", Duration.ofSeconds(10));
        assertThat(error.path("payload").path("code").asText()).isEqualTo("CHAT_NOT_PARTICIPANT");
        assertThat(error.path("payload").path("ref_id").asText()).isEqualTo("f-1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages", Integer.class)).isZero();
    }

    @Test
    void el_ticket_es_de_un_solo_uso_y_uno_invalido_cierra_con_4401() {
        ResponseEntity<JsonNode> ticket = call(HttpMethod.POST, "/api/chat/v1/ws-tickets", token(CLIENT), null);
        String value = ticket.getBody().path("ticket").asText();
        assertThat(ticket.getBody().path("expires_in").asInt()).isEqualTo(30);

        WsTestClient first = WsTestClient.connect("ws://localhost:" + port + "/api/chat/v1/ws?ticket=" + value);
        clients.add(first);
        await().atMost(Duration.ofSeconds(10)).until(() -> onlineUsers.onlineAmong(List.of(CLIENT)).contains(CLIENT));
        WsTestClient reused = WsTestClient.connect("ws://localhost:" + port + "/api/chat/v1/ws?ticket=" + value);
        clients.add(reused);
        WsTestClient bogus = WsTestClient.connect("ws://localhost:" + port + "/api/chat/v1/ws?ticket=nope");
        clients.add(bogus);
        WsTestClient none = WsTestClient.connect("ws://localhost:" + port + "/api/chat/v1/ws");
        clients.add(none);

        assertThat(reused.awaitClose(Duration.ofSeconds(10))).isEqualTo(4401);
        assertThat(bogus.awaitClose(Duration.ofSeconds(10))).isEqualTo(4401);
        assertThat(none.awaitClose(Duration.ofSeconds(10))).isEqualTo(4401);
        assertThat(first.isClosed()).isFalse();
    }

    @Test
    void un_frame_demasiado_grande_cierra_la_conexion() {
        long id = openConversation(REQUEST_IDS.incrementAndGet(), CLIENT, OFFERER);
        WsTestClient client = connectWs(CLIENT);
        client.sendRaw("{\"type\":\"message.send\",\"payload\":{\"conversation_id\":" + id + ",\"client_message_id\":\"big\",\"body\":\""
                + "a".repeat(9 * 1024) + "\"}}");
        assertThat(client.awaitClose(Duration.ofSeconds(10))).isEqualTo(1009);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages", Integer.class)).isZero();
    }

    @Test
    void frames_mal_formados_o_desconocidos_devuelven_error_sin_cerrar_la_conexion() {
        WsTestClient client = connectWs(CLIENT);
        client.sendRaw("esto no es json");
        assertThat(client.await("error", Duration.ofSeconds(10)).path("payload").path("code").asText()).isEqualTo("CHAT_BAD_FRAME");
        client.send("no.existe", "f-9", Map.of());
        assertThat(client.await("error", Duration.ofSeconds(10)).path("payload").path("code").asText()).isEqualTo("CHAT_UNKNOWN_TYPE");
        client.send("message.send", "f-10", Map.of("body", "sin conversación"));
        assertThat(client.await("error", Duration.ofSeconds(10)).path("payload").path("code").asText()).isEqualTo("CHAT_BAD_FRAME");
        client.send("ping", "p-1", Map.of());
        assertThat(client.await("pong", Duration.ofSeconds(10)).path("id").asText()).isEqualTo("p-1");
    }

    @Test
    void el_origen_no_permitido_en_el_handshake_se_rechaza() throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.WebSocket.Builder builder = client.newWebSocketBuilder().header("Origin", "http://evil.example");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> builder.buildAsync(
                java.net.URI.create("ws://localhost:" + port + "/api/chat/v1/ws?ticket=x"),
                new java.net.http.WebSocket.Listener() { }).join()).hasStackTraceContaining("403");
    }

    // ================= criterio 6: solo lectura =================

    @Test
    void al_cancelarse_la_solicitud_la_conversacion_pasa_a_solo_lectura_y_el_historial_sigue_disponible() {
        long requestId = REQUEST_IDS.incrementAndGet();
        long id = openConversation(requestId, CLIENT, OFFERER);
        WsTestClient client = connectWs(CLIENT);
        client.send("message.send", "f-1", send(id, "cid-1", "antes de cancelar"));
        client.await("message.ack", Duration.ofSeconds(10));

        publishEvent("serviya.requests", "requests.service_request.status_changed", UUID.randomUUID().toString(),
                Instant.now().plusSeconds(5), Map.of("request_id", requestId, "status", "CANCELLED"));

        JsonNode changed = client.await("conversation.status_changed", Duration.ofSeconds(15));
        assertThat(changed.path("payload").path("status").asText()).isEqualTo("READ_ONLY");
        assertThat(changed.path("payload").path("context_status").asText()).isEqualTo("CANCELLED");

        ResponseEntity<JsonNode> write = call(HttpMethod.POST, "/api/chat/v1/conversations/" + id + "/messages", token(OFFERER),
                Map.of("client_message_id", "late", "body", "¿sigues ahí?"));
        assertThat(write.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(write.getBody().path("code").asText()).isEqualTo("CHAT_CONVERSATION_READ_ONLY");
        client.send("message.send", "f-2", send(id, "cid-late", "tarde"));
        assertThat(client.await("error", Duration.ofSeconds(10)).path("payload").path("code").asText())
                .isEqualTo("CHAT_CONVERSATION_READ_ONLY");
        assertThat(get("/api/chat/v1/conversations/" + id + "/messages", OFFERER).getBody().path("items")).hasSize(1);
    }

    @Test
    void el_listado_filtra_por_estado_de_la_conversacion_de_la_solicitud_y_por_ambos() {
        long open = openConversation(REQUEST_IDS.incrementAndGet(), CLIENT, 2001);
        long cancelledRequest = REQUEST_IDS.incrementAndGet();
        long cancelled = openConversation(cancelledRequest, CLIENT, 2002);
        publishEvent("serviya.requests", "requests.service_request.status_changed", UUID.randomUUID().toString(),
                Instant.now().plusSeconds(5), Map.of("request_id", cancelledRequest, "status", "CANCELLED"));
        await().atMost(Duration.ofSeconds(15)).until(() ->
                get("/api/chat/v1/conversations/" + cancelled, CLIENT).getBody().path("status").asText().equals("READ_ONLY"));

        assertThat(ids(get("/api/chat/v1/conversations?status=ACTIVE", CLIENT))).containsExactly(open);
        assertThat(ids(get("/api/chat/v1/conversations?status=READ_ONLY", CLIENT))).containsExactly(cancelled);
        assertThat(ids(get("/api/chat/v1/conversations?request_status=CANCELLED", CLIENT))).containsExactly(cancelled);
        assertThat(ids(get("/api/chat/v1/conversations?request_status=PENDING,CANCELLED", CLIENT))).containsExactlyInAnyOrder(open, cancelled);
        assertThat(ids(get("/api/chat/v1/conversations?status=ACTIVE&request_status=CANCELLED", CLIENT))).isEmpty();
        assertThat(get("/api/chat/v1/conversations?status=NOPE", CLIENT).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/chat/v1/conversations?cursor=%%%", CLIENT).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static List<Long> ids(ResponseEntity<JsonNode> response) {
        List<Long> ids = new ArrayList<>();
        response.getBody().path("items").forEach(n -> ids.add(n.path("id").asLong()));
        return ids;
    }

    @Test
    void la_paginacion_del_listado_por_cursor_recorre_todo_sin_repetir() {
        for (int i = 0; i < 5; i++) {
            openConversation(REQUEST_IDS.incrementAndGet(), CLIENT, 3000 + i);
        }
        List<Long> seen = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 10; page++) {
            JsonNode body = get("/api/chat/v1/conversations?limit=2" + (cursor == null ? "" : "&cursor=" + cursor), CLIENT).getBody();
            body.path("items").forEach(n -> seen.add(n.path("id").asLong()));
            if (body.path("next_cursor").isNull()) {
                break;
            }
            cursor = body.path("next_cursor").asText();
        }
        assertThat(seen).hasSize(5).doesNotHaveDuplicates();
    }

    // ================= eventos entrantes: idempotencia y DLQ =================

    @Test
    void el_mismo_evento_dos_veces_crea_una_sola_conversacion() {
        long requestId = REQUEST_IDS.incrementAndGet();
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> data = Map.of("request_id", requestId, "client_id", CLIENT, "offerer_id", OFFERER, "status", "PENDING");
        publishEvent("serviya.requests", "requests.service_request.created", eventId, Instant.now(), data);
        publishEvent("serviya.requests", "requests.service_request.created", eventId, Instant.now(), data);
        // y una copia con OTRO event_id para la misma solicitud (reenvío del productor)
        publishEvent("serviya.requests", "requests.service_request.created", UUID.randomUUID().toString(), Instant.now(), data);

        await().atMost(Duration.ofSeconds(15)).until(() -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM processed_events", Integer.class) >= 2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE context_id = ?", Integer.class, requestId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE routing_key = 'chat.conversation.created'", Integer.class)).isEqualTo(1);
    }

    @Test
    void un_evento_invalido_va_a_la_dlq_sin_reintentos_y_no_bloquea_los_siguientes() throws Exception {
        amqp.send("serviya.requests", "requests.service_request.created", MessageBuilder.withBody("esto no es json".getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build());
        publishEvent("serviya.requests", "requests.service_request.created", UUID.randomUUID().toString(), Instant.now(),
                Map.of("request_id", "no-es-un-numero", "client_id", CLIENT, "offerer_id", OFFERER, "status", "PENDING"));

        Message first = amqp.receive("chat-service.requests.dlq", 15_000);
        Message second = amqp.receive("chat-service.requests.dlq", 15_000);
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();

        long id = openConversation(REQUEST_IDS.incrementAndGet(), CLIENT, OFFERER); // la cola sigue viva
        assertThat(id).isPositive();
    }

    @Test
    void un_cambio_de_estado_sin_conversacion_se_reintenta_y_termina_en_la_dlq() {
        publishEvent("serviya.requests", "requests.service_request.status_changed", UUID.randomUUID().toString(), Instant.now(),
                Map.of("request_id", 999_999, "status", "CANCELLED"));
        assertThat(amqp.receive("chat-service.requests.dlq", 20_000)).isNotNull();
    }

    // ================= contrato con el productor real (monolito: ChatEventPublisher) =================

    /** JSON con la forma EXACTA que serializa DomainEventOutboxPersistenceAdapter del monolito. */
    private void publishLikeMonolith(String exchange, String type, String subject, String dataJson) {
        String body = "{\"event_id\":\"" + UUID.randomUUID() + "\",\"event_type\":\"" + type + "\",\"event_version\":1,"
                + "\"occurred_at\":\"" + Instant.now() + "\",\"producer\":\"serviya-monolith\",\"subject\":\"" + subject + "\","
                + "\"correlation_id\":null,\"causation_id\":null,\"data\":" + dataJson + "}";
        amqp.send(exchange, type, MessageBuilder.withBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build());
    }

    @Test
    void los_eventos_con_la_forma_exacta_del_monolito_se_consumen_y_los_tipos_desconocidos_se_ignoran() throws Exception {
        long requestId = REQUEST_IDS.incrementAndGet();
        publishLikeMonolith("serviya.users", "users.user.registered", "user/" + OFFERER,
                "{\"user_id\":" + OFFERER + ",\"display_name\":\"Beto Oferente\",\"avatar_url\":null,\"roles\":[\"OFFERER\"],\"status\":\"ACTIVE\"}");
        publishLikeMonolith("serviya.requests", "requests.service_request.created", "service_request/" + requestId,
                "{\"request_id\":" + requestId + ",\"client_id\":" + CLIENT + ",\"offerer_id\":" + OFFERER
                        + ",\"service_id\":3,\"status\":\"PENDING\",\"created_at\":\"2026-11-09T12:00:00\"}");
        // display_name null en updated = «no cambió»
        publishLikeMonolith("serviya.users", "users.user.updated", "user/" + OFFERER,
                "{\"user_id\":" + OFFERER + ",\"display_name\":null,\"avatar_url\":\"http://img/beto.png\"}");
        // un tipo que el chat no usa llega por el comodín y NO debe ir a la DLQ
        publishLikeMonolith("serviya.requests", "requests.service_request.accepted", "service_request/" + requestId,
                "{\"request_id\":" + requestId + "}");

        await().atMost(Duration.ofSeconds(15)).until(() ->
                get("/api/chat/v1/conversations/by-request/" + requestId, CLIENT).getStatusCode() == HttpStatus.OK);
        await().atMost(Duration.ofSeconds(15)).until(() -> "http://img/beto.png".equals(jdbc.queryForList(
                "SELECT avatar_url FROM user_directory WHERE user_id = ?", String.class, OFFERER).stream().findFirst().orElse(null)));
        JsonNode item = get("/api/chat/v1/conversations", CLIENT).getBody().path("items").get(0);
        assertThat(item.path("counterpart").path("display_name").asText()).isEqualTo("Beto Oferente");

        publishLikeMonolith("serviya.requests", "requests.service_request.status_changed", "service_request/" + requestId,
                "{\"request_id\":" + requestId + ",\"previous_status\":\"PENDING\",\"status\":\"REJECTED\",\"changed_by\":"
                        + OFFERER + ",\"changed_at\":\"2026-11-09T12:05:00\"}");
        await().atMost(Duration.ofSeconds(15)).until(() -> "READ_ONLY".equals(
                get("/api/chat/v1/conversations/by-request/" + requestId, CLIENT).getBody().path("status").asText()));
        assertThat(amqp.receive("chat-service.requests.dlq", 1000)).as("nada a la DLQ").isNull();
        assertThat(amqp.receive("chat-service.users.dlq", 1000)).isNull();
    }

    @Test
    void los_eventos_que_publica_el_chat_llevan_el_sobre_acordado_con_subject_y_causalidad() throws Exception {
        String causingEvent = UUID.randomUUID().toString();
        long requestId = REQUEST_IDS.incrementAndGet();
        publishEvent("serviya.requests", "requests.service_request.created", causingEvent, Instant.now(),
                Map.of("request_id", requestId, "client_id", CLIENT, "offerer_id", OFFERER, "status", "PENDING"));
        await().atMost(Duration.ofSeconds(15)).until(() ->
                get("/api/chat/v1/conversations/by-request/" + requestId, CLIENT).getStatusCode() == HttpStatus.OK);
        long id = get("/api/chat/v1/conversations/by-request/" + requestId, CLIENT).getBody().path("id").asLong();

        JsonNode created = relayAndCapture().stream()
                .filter(n -> "chat.conversation.created".equals(n.path("event_type").asText())).findFirst().orElseThrow();
        assertThat(created.path("producer").asText()).isEqualTo("chat-service");
        assertThat(created.path("subject").asText()).isEqualTo("conversation/" + id);
        assertThat(created.path("causation_id").asText()).isEqualTo(causingEvent);
        assertThat(created.path("event_version").asInt()).isEqualTo(1);
        assertThat(created.path("correlation_id").asText()).isNotBlank();
    }

    // ================= usuarios (RF-CHAT-30) =================

    @Test
    void un_usuario_suspendido_no_puede_enviar_y_al_reactivarse_si() {
        long id = openConversation(REQUEST_IDS.incrementAndGet(), CLIENT, OFFERER);
        publishEvent("serviya.users", "users.user.registered", UUID.randomUUID().toString(), Instant.now(),
                Map.of("user_id", CLIENT, "display_name", "Ana Cliente"));
        publishEvent("serviya.users", "users.user.suspended", UUID.randomUUID().toString(), Instant.now().plusSeconds(1),
                Map.of("user_id", CLIENT));
        await().atMost(Duration.ofSeconds(15)).until(() -> "SUSPENDED".equals(
                jdbc.queryForList("SELECT status FROM user_directory WHERE user_id = ?", String.class, CLIENT).stream().findFirst().orElse("")));

        ResponseEntity<JsonNode> blocked = call(HttpMethod.POST, "/api/chat/v1/conversations/" + id + "/messages", token(CLIENT),
                Map.of("client_message_id", "a", "body", "hola"));
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(blocked.getBody().path("code").asText()).isEqualTo("CHAT_USER_SUSPENDED");
        // la conexión WebSocket de un suspendido se cierra con 4403
        ResponseEntity<JsonNode> ticket = call(HttpMethod.POST, "/api/chat/v1/ws-tickets", token(CLIENT), null);
        WsTestClient ws = WsTestClient.connect("ws://localhost:" + port + "/api/chat/v1/ws?ticket=" + ticket.getBody().path("ticket").asText());
        clients.add(ws);
        assertThat(ws.awaitClose(Duration.ofSeconds(10))).isEqualTo(4403);

        publishEvent("serviya.users", "users.user.reactivated", UUID.randomUUID().toString(), Instant.now().plusSeconds(2),
                Map.of("user_id", CLIENT));
        await().atMost(Duration.ofSeconds(15)).until(() -> call(HttpMethod.POST, "/api/chat/v1/conversations/" + id + "/messages",
                token(CLIENT), Map.of("client_message_id", "b", "body", "hola")).getStatusCode() == HttpStatus.CREATED);
    }

    // ================= permisos del usuario RabbitMQ `chat` (infra/rabbitmq/setup-topology.sh) =================

    @Test
    void el_usuario_chat_no_puede_publicar_en_los_exchanges_de_otros_dominios_pero_si_en_los_suyos() {
        for (String foreign : new String[] {"serviya.requests", "serviya.users"}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> chatRabbit.invoke(ops -> {
                ops.convertAndSend(foreign, "requests.service_request.created", "{}");
                ops.waitForConfirmsOrDie(3000);
                return null;
            })).as("publicar en " + foreign).hasStackTraceContaining("ACCESS_REFUSED");
        }
        for (String own : new String[] {"serviya.chat", "serviya.notifications"}) {
            chatRabbit.invoke(ops -> {
                ops.convertAndSend(own, "test.permissions", "{}");
                ops.waitForConfirmsOrDie(3000);
                return null;
            });
        }
    }

    // ================= eventos salientes: outbox → RabbitMQ =================

    @Test
    void con_el_destinatario_desconectado_se_emite_la_notificacion_generica_y_el_evento_de_metricas_sin_contenido() throws Exception {
        long id = openConversation(REQUEST_IDS.incrementAndGet(), CLIENT, OFFERER);
        publishEvent("serviya.users", "users.user.registered", UUID.randomUUID().toString(), Instant.now(),
                Map.of("user_id", CLIENT, "display_name", "Ana Cliente"));
        await().atMost(Duration.ofSeconds(15)).until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM user_directory", Integer.class) == 1);
        WsTestClient client = connectWs(CLIENT);           // el oferente NO está conectado

        client.send("message.send", "f-1", send(id, "cid-1", "Detalles privados del trabajo 3001234567"));
        client.await("message.ack", Duration.ofSeconds(10));
        List<JsonNode> published = relayAndCapture();

        JsonNode notification = published.stream().filter(n -> n.has("schemaVersion")).findFirst().orElseThrow();
        assertThat(notification.path("schemaVersion").asInt()).isEqualTo(1);           // contrato notification.requested.v1
        assertThat(notification.path("userId").asLong()).isEqualTo(OFFERER);
        assertThat(notification.path("message").asText()).isEqualTo("Tienes un mensaje nuevo de Ana Cliente");
        assertThat(notification.path("entityType").asText()).isEqualTo("CONVERSATION");
        assertThat(notification.path("entityId").asLong()).isEqualTo(id);
        assertThat(notification.path("channels")).extracting(JsonNode::asText).containsExactly("INTERNAL");
        assertThat(notification.path("eventId").asText()).isNotBlank();

        JsonNode sent = published.stream().filter(n -> "chat.message.sent".equals(n.path("event_type").asText())).findFirst().orElseThrow();
        assertThat(sent.path("producer").asText()).isEqualTo("chat-service");
        assertThat(sent.path("data").path("recipient_online").asBoolean()).isFalse();
        assertThat(sent.path("data").path("flags")).extracting(JsonNode::asText).containsExactly("CONTACT_INFO");
        // DP-04: ni el evento ni la notificación llevan el contenido del mensaje
        for (JsonNode event : published) {
            assertThat(event.toString()).doesNotContain("Detalles privados", "3001234567");
        }
        assertThat(published.stream().anyMatch(n -> "chat.conversation.created".equals(n.path("event_type").asText()))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL", Integer.class)).isZero();
    }

    @Test
    void con_el_destinatario_conectado_no_se_emite_notificacion() throws Exception {
        long id = openConversation(REQUEST_IDS.incrementAndGet(), CLIENT, OFFERER);
        WsTestClient client = connectWs(CLIENT);
        WsTestClient offerer = connectWs(OFFERER);
        client.send("message.send", "f-1", send(id, "cid-1", "Hola"));
        client.await("message.ack", Duration.ofSeconds(10));
        offerer.await("message.new", Duration.ofSeconds(10));

        List<JsonNode> published = relayAndCapture();

        assertThat(published.stream().noneMatch(n -> n.has("schemaVersion"))).isTrue();
        JsonNode sent = published.stream().filter(n -> "chat.message.sent".equals(n.path("event_type").asText())).findFirst().orElseThrow();
        assertThat(sent.path("data").path("recipient_online").asBoolean()).isTrue();
    }

    @Test
    void con_rabbitmq_detenido_los_eventos_esperan_en_el_outbox_y_se_publican_al_volver_criterio_8() throws Exception {
        long id = openConversation(REQUEST_IDS.incrementAndGet(), CLIENT, OFFERER);
        relayAndCapture(); // vacía lo previo (conversation.created)

        var docker = org.testcontainers.DockerClientFactory.instance().client();
        docker.pauseContainerCmd(rabbit.getContainerId()).exec();
        try {
            ResponseEntity<JsonNode> sent = call(HttpMethod.POST, "/api/chat/v1/conversations/" + id + "/messages", token(CLIENT),
                    Map.of("client_message_id", "durante-la-caida", "body", "Hola"));
            assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);     // el negocio no se entera de la caída
            assertThat(outbox.publishPendingBatch()).isZero();                  // el relay falla…
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL", Integer.class))
                    .isGreaterThanOrEqualTo(1);                                  // …y nada se pierde
            assertThat(jdbc.queryForObject("SELECT MAX(attempts) FROM outbox_events", Integer.class)).isGreaterThanOrEqualTo(1);
        } finally {
            docker.unpauseContainerCmd(rabbit.getContainerId()).exec();
        }

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).until(() -> {
            try {
                return outbox.publishPendingBatch() > 0;
            } catch (RuntimeException e) {
                return false;
            }
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL", Integer.class)).isZero();
        Message m = amqp.receive(capturedQueue, 5000);
        assertThat(m).isNotNull();
    }
}
