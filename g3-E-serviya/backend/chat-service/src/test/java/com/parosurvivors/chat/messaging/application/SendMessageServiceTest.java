package com.parosurvivors.chat.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parosurvivors.chat.conversations.domain.Conversation;
import com.parosurvivors.chat.conversations.domain.TerminalRequestStatuses;
import com.parosurvivors.chat.directory.domain.DirectoryUser;
import com.parosurvivors.chat.directory.domain.UserStatus;
import com.parosurvivors.chat.messaging.application.dto.SendMessageResult;
import com.parosurvivors.chat.messaging.application.services.SendMessageService;
import com.parosurvivors.chat.shared.application.ChatEvents;
import com.parosurvivors.chat.support.Fakes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SendMessageServiceTest {

    private static final long CLIENT = 10;
    private static final long OFFERER = 20;
    private static final long STRANGER = 99;
    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    private static final TerminalRequestStatuses TERMINAL = TerminalRequestStatuses.of("CANCELLED", "COMPLETED");

    private final List<String> timeline = new ArrayList<>();
    private final Fakes.InMemoryConversations conversations = new Fakes.InMemoryConversations();
    private final Fakes.InMemoryMessages messages = new Fakes.InMemoryMessages();
    private final Fakes.InMemoryUsers users = new Fakes.InMemoryUsers();
    private final Fakes.RecordingEvents events = new Fakes.RecordingEvents();
    private final Fakes.RecordingNotifications notifications = new Fakes.RecordingNotifications();
    private final Fakes.RecordingRealtime realtime = new Fakes.RecordingRealtime();
    private final Fakes.StubOnlineUsers online = new Fakes.StubOnlineUsers();
    private final Fakes.StubRateLimiter rateLimiter = new Fakes.StubRateLimiter();
    private SendMessageService service;
    private long conversationId;

    @BeforeEach
    void setUp() {
        realtime.log = timeline;
        service = new SendMessageService(conversations, messages, users, events, notifications, realtime, online,
                rateLimiter, new Fakes.DirectUnitOfWork(timeline), Fakes.clockAt(NOW), 2000);
        conversationId = conversations.save(
                Conversation.openForRequest(7, CLIENT, OFFERER, "PENDING", NOW.minusSeconds(60), TERMINAL)).id();
        users.save(new DirectoryUser(CLIENT, "Ana Cliente", null, UserStatus.ACTIVE, NOW));
        users.save(new DirectoryUser(OFFERER, "Beto Oferente", null, UserStatus.ACTIVE, NOW));
    }

    private SendMessageResult send(long sender, String cid, String body) {
        return service.execute(new SendMessageService.Command(sender, conversationId, cid, body));
    }

    // --- camino feliz ---

    @Test
    void un_participante_envia_un_mensaje_que_queda_guardado_con_seq_1() {
        SendMessageResult r = send(CLIENT, "c-1", "Hola");

        assertThat(r.duplicate()).isFalse();
        assertThat(r.message().seq()).isEqualTo(1);
        assertThat(r.message().id()).isNotNull();
        assertThat(r.warnings()).isEmpty();
        assertThat(messages.store).hasSize(1);
        Conversation c = conversations.findById(conversationId).orElseThrow();
        assertThat(c.lastSeq()).isEqualTo(1);
        assertThat(c.lastMessageAt()).isEqualTo(NOW);
        assertThat(c.unreadFor(OFFERER)).isEqualTo(1);
        assertThat(c.unreadFor(CLIENT)).isZero();
    }

    @Test
    void los_envios_sucesivos_de_ambos_lados_numeran_1_2_3() {
        assertThat(send(CLIENT, "a", "uno").message().seq()).isEqualTo(1);
        assertThat(send(OFFERER, "b", "dos").message().seq()).isEqualTo(2);
        assertThat(send(CLIENT, "c", "tres").message().seq()).isEqualTo(3);
    }

    @Test
    void el_envio_bloquea_la_fila_de_la_conversacion() {
        send(CLIENT, "a", "uno");
        assertThat(conversations.lockedIds).containsExactly(conversationId);
    }

    // --- RF-CHAT-16: idempotencia ---

    @Test
    void reenviar_con_el_mismo_client_message_id_no_duplica_ni_vuelve_a_entregar_ni_a_notificar() {
        SendMessageResult first = send(CLIENT, "c-1", "Hola");
        timeline.clear();
        events.published.clear();
        notifications.requests.clear();

        SendMessageResult retry = send(CLIENT, "c-1", "Hola");

        assertThat(retry.duplicate()).isTrue();
        assertThat(retry.message().id()).isEqualTo(first.message().id());
        assertThat(retry.message().seq()).isEqualTo(1);
        assertThat(messages.store).hasSize(1);
        assertThat(conversations.findById(conversationId).orElseThrow().lastSeq()).isEqualTo(1);
        assertThat(timeline).doesNotContain("message.new");
        assertThat(events.published).isEmpty();
        assertThat(notifications.requests).isEmpty();
    }

    @Test
    void el_mismo_client_message_id_de_otro_emisor_es_otro_mensaje() {
        send(CLIENT, "same", "Hola");
        SendMessageResult other = send(OFFERER, "same", "Hola también");
        assertThat(other.duplicate()).isFalse();
        assertThat(messages.store).hasSize(2);
    }

    @Test
    void client_message_id_vacio_o_demasiado_largo_se_rechaza() {
        assertThatThrownBy(() -> send(CLIENT, " ", "Hola")).extracting("code")
                .isEqualTo("CHAT_INVALID_CLIENT_MESSAGE_ID");
        assertThatThrownBy(() -> send(CLIENT, "x".repeat(37), "Hola")).extracting("code")
                .isEqualTo("CHAT_INVALID_CLIENT_MESSAGE_ID");
        assertThatThrownBy(() -> send(CLIENT, null, "Hola")).extracting("code")
                .isEqualTo("CHAT_INVALID_CLIENT_MESSAGE_ID");
    }

    // --- autorización y estado ---

    @Test
    void un_no_participante_no_puede_escribir_y_no_se_guarda_nada() {
        assertThatThrownBy(() -> send(STRANGER, "x", "Hola")).extracting("code").isEqualTo("CHAT_NOT_PARTICIPANT");
        assertThat(messages.store).isEmpty();
        assertThat(events.published).isEmpty();
    }

    @Test
    void un_no_participante_no_descubre_mensajes_ajenos_reenviando_un_client_message_id() {
        send(CLIENT, "secret", "Hola");
        assertThatThrownBy(() -> send(STRANGER, "secret", "Hola")).extracting("code")
                .isEqualTo("CHAT_NOT_PARTICIPANT");
    }

    @Test
    void conversacion_inexistente_se_reporta_como_no_encontrada() {
        assertThatThrownBy(() -> service.execute(new SendMessageService.Command(CLIENT, 12345, "x", "Hola")))
                .extracting("code").isEqualTo("CHAT_CONVERSATION_NOT_FOUND");
    }

    @Test
    void no_se_escribe_en_una_conversacion_en_solo_lectura() {
        Conversation c = conversations.findById(conversationId).orElseThrow();
        c.applyRequestStatus("CANCELLED", NOW, TERMINAL);
        conversations.save(c);

        assertThatThrownBy(() -> send(CLIENT, "x", "Hola")).extracting("code")
                .isEqualTo("CHAT_CONVERSATION_READ_ONLY");
        assertThat(messages.store).isEmpty();
    }

    @Test
    void un_usuario_suspendido_no_puede_enviar() {
        users.save(new DirectoryUser(CLIENT, "Ana Cliente", null, UserStatus.SUSPENDED, NOW));
        assertThatThrownBy(() -> send(CLIENT, "x", "Hola")).extracting("code").isEqualTo("CHAT_USER_SUSPENDED");
        assertThat(messages.store).isEmpty();
    }

    @Test
    void un_usuario_sin_proyeccion_puede_enviar_porque_el_directorio_puede_ir_atrasado() {
        users.store.clear();
        assertThat(send(CLIENT, "x", "Hola").message().seq()).isEqualTo(1);
    }

    // --- validación del contenido ---

    @Test
    void mensaje_vacio_o_demasiado_largo_se_rechaza_sin_consumir_secuencia() {
        assertThatThrownBy(() -> send(CLIENT, "a", "   ")).extracting("code").isEqualTo("CHAT_MESSAGE_EMPTY");
        assertThatThrownBy(() -> send(CLIENT, "b", "a".repeat(2001))).extracting("code")
                .isEqualTo("CHAT_MESSAGE_TOO_LONG");
        assertThat(send(CLIENT, "c", "ok").message().seq()).isEqualTo(1);
    }

    // --- RF-CHAT-32: advertir sin bloquear ---

    @Test
    void mensaje_con_telefono_se_envia_pero_el_ack_trae_la_advertencia_y_el_mensaje_queda_marcado() {
        SendMessageResult r = send(CLIENT, "a", "llámame al 3001234567");
        assertThat(r.warnings()).containsExactly("CONTACT_INFO");
        assertThat(r.message().hasContactInfo()).isTrue();
        assertThat(messages.store).hasSize(1);
        assertThat(events.of(ChatEvents.MESSAGE_SENT).get(0).data()).containsEntry("flags", List.of("CONTACT_INFO"));
    }

    @Test
    void un_precio_no_dispara_la_advertencia() {
        assertThat(send(CLIENT, "a", "cuesta $150.000").warnings()).isEmpty();
    }

    // --- RNF-CHAT-12 ---

    @Test
    void si_se_supera_el_limite_de_envio_se_rechaza_y_no_se_guarda() {
        rateLimiter.allow = false;
        assertThatThrownBy(() -> send(CLIENT, "a", "Hola")).extracting("code").isEqualTo("CHAT_RATE_LIMITED");
        assertThat(messages.store).isEmpty();
    }

    // --- tiempo real: solo tras el commit, best effort ---

    @Test
    void la_difusion_en_tiempo_real_ocurre_despues_del_commit() {
        send(CLIENT, "a", "Hola");
        assertThat(timeline).containsExactly("commit", "message.new");
    }

    @Test
    void el_mensaje_se_difunde_al_otro_participante_y_a_las_otras_sesiones_del_emisor() {
        send(CLIENT, "a", "Hola");
        assertThat(realtime.recipientsByEvent.get("message.new")).containsExactlyInAnyOrder(CLIENT, OFFERER);
    }

    @Test
    void si_el_tiempo_real_falla_el_mensaje_igual_queda_guardado_y_confirmado() {
        realtime.failing = true;
        SendMessageResult r = send(CLIENT, "a", "Hola");
        assertThat(r.message().seq()).isEqualTo(1);
        assertThat(messages.store).hasSize(1);
    }

    // --- RF-CHAT-15 / DP-04 / ADR-011: el chat decide y compone ---

    @Test
    void con_el_destinatario_desconectado_el_chat_emite_un_comando_de_notificacion_generico() {
        send(CLIENT, "a", "Mensaje privado con detalles del trabajo");

        assertThat(notifications.requests).hasSize(1);
        var n = notifications.requests.get(0);
        assertThat(n.recipientId()).isEqualTo(OFFERER);
        assertThat(n.message()).isEqualTo("Tienes un mensaje nuevo de Ana Cliente");
        assertThat(n.entityType()).isEqualTo("CONVERSATION");
        assertThat(n.entityId()).isEqualTo(conversationId);
        // Nunca el contenido del mensaje (DP-04).
        assertThat(n.title() + n.message()).doesNotContain("Mensaje privado", "detalles");
    }

    @Test
    void con_el_destinatario_conectado_no_se_notifica() {
        online.online.add(OFFERER);
        send(CLIENT, "a", "Hola");
        assertThat(notifications.requests).isEmpty();
        assertThat(events.of(ChatEvents.MESSAGE_SENT).get(0).data()).containsEntry("recipient_online", true);
    }

    @Test
    void si_no_se_puede_saber_quien_esta_conectado_se_notifica_por_si_acaso() {
        online.failing = true;
        send(CLIENT, "a", "Hola");
        assertThat(notifications.requests).hasSize(1);
    }

    @Test
    void sin_nombre_en_el_directorio_el_aviso_usa_un_texto_neutro() {
        users.store.remove(CLIENT);
        send(CLIENT, "a", "Hola");
        assertThat(notifications.requests.get(0).message()).isEqualTo("Tienes un mensaje nuevo de un usuario");
    }

    // --- RF-CHAT: evento para Métricas sin contenido (DP-04) ---

    @Test
    void se_registra_chat_message_sent_en_el_outbox_sin_contenido_ni_vista_previa() {
        SendMessageResult r = send(CLIENT, "a", "texto secreto");

        var published = events.of(ChatEvents.MESSAGE_SENT);
        assertThat(published).hasSize(1);
        assertThat(published.get(0).exchange()).isEqualTo(ChatEvents.EXCHANGE);
        assertThat(published.get(0).data())
                .containsEntry("conversation_id", conversationId)
                .containsEntry("message_id", r.message().id())
                .containsEntry("seq", 1L)
                .containsEntry("sender_id", CLIENT)
                .containsEntry("recipient_ids", List.of(OFFERER))
                .containsEntry("recipient_online", false)
                .doesNotContainKey("body");
        assertThat(published.get(0).data().toString()).doesNotContain("texto secreto");
    }
}
