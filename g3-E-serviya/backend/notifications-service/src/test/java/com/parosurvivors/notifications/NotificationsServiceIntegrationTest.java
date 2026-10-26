package com.parosurvivors.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parosurvivors.notifications.application.ports.input.NotificationDeliveryServicePort;
import com.parosurvivors.notifications.infrastructure.messaging.NotificationMessagingConfig;
import com.parosurvivors.notifications.infrastructure.messaging.PayloadSealerTest;
import com.parosurvivors.notifications.infrastructure.security.JwtTokenVerifierTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Punta a punta contra MySQL y RabbitMQ REALES (Testcontainers): el mensaje que publicaría el outbox del
 * monolito llega por el bus, se registra en la BD propia (esquema Flyway validado por Hibernate) y aparece
 * en la bandeja REST del usuario. Se SALTA si no hay Docker.
 */
@SpringBootTest(properties = {
        "serviya.security.jwt-secret=" + JwtTokenVerifierTest.SECRET,
        "serviya.security.encryption-key=serviya-test-notifications-at-rest-key-0123456789",
        "serviya.messaging.payload-key=" + PayloadSealerTest.KEY,
        "serviya.notifications.cron.retry-failed=-",          // sin scheduler: el test dispara el reintento
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class NotificationsServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NotificationDeliveryServicePort deliveryService;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM notification_deliveries");
        jdbc.update("DELETE FROM notifications");
    }

    private static String bearer(long userId) throws Exception {
        return "Bearer " + JwtTokenVerifierTest.monolithToken(JwtTokenVerifierTest.SECRET, userId, List.of("CLIENT"), 60_000);
    }

    private void publish(String json) {
        Message message = MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        rabbitTemplate.send(NotificationMessagingConfig.EXCHANGE, NotificationMessagingConfig.ROUTING_KEY, message);
    }

    private static String internalMessage(String eventId, long userId) {
        return """
                {"schemaVersion":1,"eventId":"%s","occurredAt":"2026-10-12T21:00:00Z","userId":%d,
                 "type":"request_accepted","title":"Solicitud aceptada","message":"Tu solicitud #9 fue aceptada",
                 "entityType":"SERVICE_REQUEST","entityId":9,"channels":["INTERNAL"]}""".formatted(eventId, userId);
    }

    private int countNotifications() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
    }

    @Test
    void consumedMessage_appearsInOwnersInbox_onceEvenIfRedelivered_andCanBeMarkedAsRead() throws Exception {
        String eventId = UUID.randomUUID().toString();
        publish(internalMessage(eventId, 42L));
        publish(internalMessage(eventId, 42L)); // redelivery del mismo evento (entrega ≥1)

        await().atMost(Duration.ofSeconds(15)).until(() -> countNotifications() == 1);
        Thread.sleep(500); // margen para que el duplicado también se haya procesado
        assertThat(countNotifications()).isEqualTo(1);

        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(42L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Solicitud aceptada"))
                .andExpect(jsonPath("$.content[0].deliveryStatus").value("SENT"));

        // Otro usuario no ve la notificación ni puede marcarla (sin IDOR).
        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(7L)))
                .andExpect(jsonPath("$.content.length()").value(0));
        Long deliveryId = jdbc.queryForObject("SELECT id FROM notification_deliveries", Long.class);
        mvc.perform(post("/api/v1/notifications/{id}/read", deliveryId).header("Authorization", bearer(7L)))
                .andExpect(status().isNotFound());

        // Regresión del bug de @Version del monolito: actualizar una entrega existente debe funcionar.
        mvc.perform(post("/api/v1/notifications/{id}/read", deliveryId).header("Authorization", bearer(42L)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/notifications").param("read", "true").header("Authorization", bearer(42L)))
                .andExpect(jsonPath("$.content[0].deliveryStatus").value("READ"));
    }

    @Test
    void inbox_requiresAuthentication_andUnknownRoutesAreDenied() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notification-channels").header("Authorization", bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.containsInAnyOrder("INTERNAL", "EMAIL")));
        mvc.perform(get("/api/v1/anything-else").header("Authorization", bearer(1L))).andExpect(status().isForbidden());
    }

    @Test
    void emailWithoutProvider_endsFailed_recipientIsEncryptedAtRest_andRetryReclaimsIt() throws Exception {
        String sealed = PayloadSealerTest.VECTOR; // correo ana@serviya.com + actionUrl, sellado con la clave de test
        publish("""
                {"schemaVersion":1,"eventId":"%s","userId":42,"type":"welcome","title":"Hola","message":"Bienvenido",
                 "entityType":"USER","entityId":42,"channels":["INTERNAL","EMAIL"],"sealed":"%s"}"""
                .formatted(UUID.randomUUID(), sealed));

        // serviya.email.enabled=false → no hay EmailPort: el envío AFTER_COMMIT deja la entrega FAILED (1 intento).
        await().atMost(Duration.ofSeconds(15)).until(() -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_deliveries WHERE delivery_status = 'FAILED' AND attempts = 1",
                Integer.class) == 1);

        byte[] storedEmail = jdbc.queryForObject("SELECT recipient_email FROM notifications", byte[].class);
        assertThat(new String(storedEmail, StandardCharsets.ISO_8859_1)).doesNotContain("ana@serviya.com");

        // El reintento reclama la fila con FOR UPDATE SKIP LOCKED (sintaxis real de MySQL) y suma un intento.
        assertThat(deliveryService.retryFailedDeliveries()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT attempts FROM notification_deliveries WHERE channel_id = 2",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void invalidMessage_goesToDeadLetterQueue_withoutBlockingTheQueue() {
        publish("{definitely not json");
        String eventId = UUID.randomUUID().toString();
        publish(internalMessage(eventId, 42L));

        Message dead = rabbitTemplate.receive(NotificationMessagingConfig.DEAD_LETTER_QUEUE, 15_000);
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).startsWith("{definitely");
        await().atMost(Duration.ofSeconds(15)).until(() -> countNotifications() == 1);
    }
}
