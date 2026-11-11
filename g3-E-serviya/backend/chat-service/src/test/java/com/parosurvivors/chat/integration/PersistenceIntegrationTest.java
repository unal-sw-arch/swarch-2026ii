package com.parosurvivors.chat.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parosurvivors.chat.conversations.application.dto.ConversationCursor;
import com.parosurvivors.chat.conversations.application.dto.ConversationFilter;
import com.parosurvivors.chat.conversations.application.dto.ConversationSummary;
import com.parosurvivors.chat.conversations.application.exceptions.ConversationAlreadyExistsException;
import com.parosurvivors.chat.conversations.application.ports.output.ConversationReadModel;
import com.parosurvivors.chat.conversations.application.ports.output.ConversationRepository;
import com.parosurvivors.chat.conversations.domain.Conversation;
import com.parosurvivors.chat.conversations.domain.ConversationStatus;
import com.parosurvivors.chat.conversations.domain.TerminalRequestStatuses;
import com.parosurvivors.chat.directory.application.ports.output.UserDirectory;
import com.parosurvivors.chat.directory.domain.DirectoryUser;
import com.parosurvivors.chat.directory.domain.UserStatus;
import com.parosurvivors.chat.messaging.application.ports.output.MessageRepository;
import com.parosurvivors.chat.messaging.domain.Message;
import com.parosurvivors.chat.messaging.domain.MessageFlag;
import com.parosurvivors.chat.messaging.domain.MessageKind;
import com.parosurvivors.chat.shared.application.ports.EventPublisher;
import com.parosurvivors.chat.shared.application.ports.UnitOfWork;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class PersistenceIntegrationTest extends IntegrationTestBase {

    private static final long CLIENT = 10;
    private static final long OFFERER = 20;
    private static final Instant T0 = Instant.parse("2026-11-09T12:00:00Z");
    private static final TerminalRequestStatuses TERMINAL = TerminalRequestStatuses.of("CANCELLED", "COMPLETED");

    @Autowired ConversationRepository conversations;
    @Autowired ConversationReadModel readModel;
    @Autowired MessageRepository messages;
    @Autowired UserDirectory users;
    @Autowired EventPublisher events;
    @Autowired UnitOfWork uow;

    private Conversation open(long requestId, long client, long offerer, String status, Instant at) {
        return conversations.save(Conversation.openForRequest(requestId, client, offerer, status, at, TERMINAL));
    }

    /** Envía un mensaje por el camino real: bloquear, registrar seq, guardar mensaje y conversación. */
    private Message send(long conversationId, long sender, String cid, String body, Instant at) {
        return uow.execute(() -> {
            Conversation c = conversations.findByIdForUpdate(conversationId).orElseThrow();
            long seq = c.registerMessage(sender, at);
            Message m = messages.save(Message.text(conversationId, seq, sender, cid, body, 2000, at));
            conversations.save(c);
            return m;
        });
    }

    // --- conversaciones ---

    @Test
    void guarda_y_recupera_una_conversacion_con_sus_participantes() {
        Conversation saved = open(7, CLIENT, OFFERER, "PENDING", T0);

        Conversation loaded = conversations.findById(saved.id()).orElseThrow();
        assertThat(loaded.contextId()).isEqualTo(7);
        assertThat(loaded.status()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(loaded.contextStatus()).isEqualTo("PENDING");
        assertThat(loaded.createdAt()).isEqualTo(T0);
        assertThat(loaded.lastMessageAt()).isEqualTo(T0);
        assertThat(loaded.participants()).extracting(p -> p.userId()).containsExactlyInAnyOrder(CLIENT, OFFERER);
        assertThat(conversations.findByRequestId(7).orElseThrow().id()).isEqualTo(saved.id());
        assertThat(conversations.findByRequestId(999)).isEmpty();
    }

    @Test
    void una_segunda_conversacion_para_la_misma_solicitud_se_rechaza_por_el_indice_unico() {
        open(7, CLIENT, OFFERER, "PENDING", T0);
        assertThatThrownBy(() -> open(7, 11, 21, "PENDING", T0)).isInstanceOf(ConversationAlreadyExistsException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversations", Integer.class)).isEqualTo(1);
    }

    @Test
    void actualizar_persiste_estado_y_secuencia() {
        long id = open(7, CLIENT, OFFERER, "PENDING", T0).id();
        Conversation c = conversations.findById(id).orElseThrow();
        c.applyRequestStatus("CANCELLED", T0.plusSeconds(5), TERMINAL);
        conversations.save(c);

        Conversation loaded = conversations.findById(id).orElseThrow();
        assertThat(loaded.status()).isEqualTo(ConversationStatus.READ_ONLY);
        assertThat(loaded.contextStatus()).isEqualTo("CANCELLED");
        assertThat(loaded.contextStatusAt()).isEqualTo(T0.plusSeconds(5));
    }

    @Test
    void guardar_una_copia_vieja_no_hace_retroceder_el_puntero_de_lectura() {
        long id = open(7, CLIENT, OFFERER, "PENDING", T0).id();
        send(id, CLIENT, "a", "uno", T0.plusSeconds(1));
        send(id, CLIENT, "b", "dos", T0.plusSeconds(2));
        Conversation stale = conversations.findById(id).orElseThrow();   // el oferente aún tiene last_read_seq = 0

        conversations.advanceReadPointer(id, OFFERER, 2);                // el oferente marca leído (concurrente)
        conversations.save(stale);                                       // se guarda la copia vieja

        assertThat(conversations.findById(id).orElseThrow().unreadFor(OFFERER)).isZero();
    }

    @Test
    void el_puntero_de_lectura_nunca_retrocede() {
        long id = open(7, CLIENT, OFFERER, "PENDING", T0).id();
        send(id, CLIENT, "a", "uno", T0.plusSeconds(1));
        send(id, CLIENT, "b", "dos", T0.plusSeconds(2));
        conversations.advanceReadPointer(id, OFFERER, 2);
        conversations.advanceReadPointer(id, OFFERER, 1);
        assertThat(conversations.findById(id).orElseThrow().participant(OFFERER).orElseThrow().lastReadSeq()).isEqualTo(2);
    }

    @Test
    void el_bloqueo_for_update_exige_una_transaccion() {
        long id = open(7, CLIENT, OFFERER, "PENDING", T0).id();
        assertThatThrownBy(() -> conversations.findByIdForUpdate(id)).isInstanceOf(RuntimeException.class);
    }

    // --- mensajes ---

    @Test
    void guarda_mensajes_con_sus_marcas_y_los_encuentra_por_client_message_id() {
        long id = open(7, CLIENT, OFFERER, "PENDING", T0).id();
        Message plain = send(id, CLIENT, "c-1", "Hola", T0.plusSeconds(1));
        Message flagged = send(id, OFFERER, "c-2", "llámame al 3001234567", T0.plusSeconds(2));

        assertThat(messages.findByClientMessageId(id, CLIENT, "c-1").orElseThrow().id()).isEqualTo(plain.id());
        Message loaded = messages.findByClientMessageId(id, OFFERER, "c-2").orElseThrow();
        assertThat(loaded.flags()).containsExactly(MessageFlag.CONTACT_INFO);
        assertThat(loaded.body()).isEqualTo("llámame al 3001234567");
        assertThat(loaded.createdAt()).isEqualTo(T0.plusSeconds(2));
        assertThat(messages.findByClientMessageId(id, OFFERER, "c-1")).isEmpty();
        assertThat(flagged.seq()).isEqualTo(2);
    }

    @Test
    void conserva_acentos_enies_y_emojis() {
        long id = open(7, CLIENT, OFFERER, "PENDING", T0).id();
        send(id, CLIENT, "c-1", "¿Cuándo vienes, señor? 😀", T0.plusSeconds(1));
        assertThat(messages.findPage(id, null, null, 10).get(0).body()).isEqualTo("¿Cuándo vienes, señor? 😀");
    }

    @Test
    void los_indices_unicos_rechazan_seq_repetido_y_client_message_id_repetido() {
        long id = open(7, CLIENT, OFFERER, "PENDING", T0).id();
        messages.save(Message.text(id, 1, CLIENT, "dup", "uno", 2000, T0));
        assertThatThrownBy(() -> messages.save(Message.text(id, 1, OFFERER, "otro", "dos", 2000, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> messages.save(Message.text(id, 2, CLIENT, "dup", "tres", 2000, T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void varios_mensajes_de_sistema_no_chocan_entre_si() {
        long id = open(7, CLIENT, OFFERER, "PENDING", T0).id();
        messages.save(Message.system(id, 1, "a", T0));
        messages.save(Message.system(id, 2, "b", T0));
        assertThat(messages.findPage(id, null, null, 10)).extracting(Message::kind)
                .containsExactly(MessageKind.SYSTEM, MessageKind.SYSTEM);
    }

    @Test
    void la_paginacion_del_historial_va_hacia_atras_en_orden_ascendente_sin_repetir() {
        long id = open(7, CLIENT, OFFERER, "PENDING", T0).id();
        for (int i = 1; i <= 10; i++) {
            send(id, i % 2 == 0 ? OFFERER : CLIENT, "c" + i, "m" + i, T0.plusSeconds(i));
        }
        assertThat(messages.findPage(id, null, null, 3)).extracting(Message::seq).containsExactly(8L, 9L, 10L);
        assertThat(messages.findPage(id, 8L, null, 3)).extracting(Message::seq).containsExactly(5L, 6L, 7L);
        assertThat(messages.findPage(id, 2L, null, 3)).extracting(Message::seq).containsExactly(1L);
        assertThat(messages.findPage(id, null, 8L, 50)).extracting(Message::seq).containsExactly(9L, 10L);
        assertThat(messages.findPage(id, null, 10L, 50)).isEmpty();
    }

    // --- directorio ---

    @Test
    void el_directorio_guarda_actualiza_y_recupera_usuarios() {
        users.save(new DirectoryUser(5, "Ana", "http://a/1.png", UserStatus.ACTIVE, T0));
        users.save(new DirectoryUser(5, "Ana María", null, UserStatus.SUSPENDED, T0.plusSeconds(1)));
        DirectoryUser u = users.find(5).orElseThrow();
        assertThat(u.displayName()).isEqualTo("Ana María");
        assertThat(u.avatarUrl()).isNull();
        assertThat(u.status()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(users.find(6)).isEmpty();
    }

    // --- outbox ---

    @Test
    void el_evento_se_escribe_en_el_outbox_dentro_de_la_transaccion_con_el_sobre_comun_y_sin_publicar() {
        uow.run(() -> events.publish("serviya.chat", "chat.message.sent", Map.of("conversation_id", 1L, "seq", 3L)));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM outbox_events");
        assertThat(row.get("exchange_name")).isEqualTo("serviya.chat");
        assertThat(row.get("routing_key")).isEqualTo("chat.message.sent");
        assertThat(row.get("published_at")).isNull();
        // created_at se guarda en UTC aunque la JVM esté en otra zona (el relay y las métricas dependen de ello).
        java.time.LocalDateTime createdAt = (java.time.LocalDateTime) row.get("created_at");
        assertThat(java.time.Duration.between(createdAt, java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)).abs())
                .isLessThan(java.time.Duration.ofMinutes(1));
        String payload = row.get("payload").toString();
        assertThat(payload).contains("\"producer\": \"chat-service\"", "\"event_type\": \"chat.message.sent\"",
                "\"event_version\": 1", "\"seq\": 3");
    }

    @Test
    void si_la_transaccion_se_revierte_el_evento_tambien() {
        assertThatThrownBy(() -> uow.run(() -> {
            events.publish("serviya.chat", "chat.message.sent", Map.of("x", 1));
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class)).isZero();
    }

    @Test
    void publicar_sin_transaccion_es_un_error() {
        assertThatThrownBy(() -> events.publish("serviya.chat", "chat.message.sent", Map.of("x", 1)))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class)).isZero();
    }

    // --- listado con filtros (RF-CHAT-03 y 31) ---

    private List<Long> listIds(long user, ConversationFilter filter) {
        return readModel.findForUser(user, filter, null, 50).stream().map(ConversationSummary::id).toList();
    }

    private ConversationFilter filter(Set<ConversationStatus> statuses, Set<String> requestStatuses) {
        return new ConversationFilter(statuses, requestStatuses, Set.of());
    }

    @Test
    void el_listado_ordena_por_actividad_reciente_e_incluye_no_leidos_ultimo_mensaje_y_contraparte() {
        users.save(new DirectoryUser(OFFERER, "Beto Oferente", null, UserStatus.ACTIVE, T0));
        long a = open(1, CLIENT, OFFERER, "PENDING", T0).id();
        long b = open(2, CLIENT, 21, "PENDING", T0.plusSeconds(10)).id();
        send(a, OFFERER, "x1", "primero", T0.plusSeconds(20));
        send(a, OFFERER, "x2", "último mensaje", T0.plusSeconds(30));

        List<ConversationSummary> rows = readModel.findForUser(CLIENT, ConversationFilter.none(), null, 50);

        assertThat(rows).extracting(ConversationSummary::id).containsExactly(a, b);   // a tuvo actividad más reciente
        ConversationSummary first = rows.get(0);
        assertThat(first.unreadCount()).isEqualTo(2);
        assertThat(first.counterpartId()).isEqualTo(OFFERER);
        assertThat(first.counterpartName()).isEqualTo("Beto Oferente");
        assertThat(first.lastMessage().body()).isEqualTo("último mensaje");
        assertThat(first.lastMessage().senderId()).isEqualTo(OFFERER);
        assertThat(first.lastMessageAt()).isEqualTo(T0.plusSeconds(30));
        ConversationSummary second = rows.get(1);
        assertThat(second.unreadCount()).isZero();
        assertThat(second.lastMessage()).isNull();                                   // sin mensajes aún
        assertThat(second.counterpartName()).isEqualTo("Usuario 21");               // sin proyección: texto neutro
        // El oferente de `a` ve 0 no leídos de sus propios mensajes y no ve la conversación `b`.
        assertThat(listIds(OFFERER, ConversationFilter.none())).containsExactly(a);
        assertThat(readModel.findForUser(OFFERER, ConversationFilter.none(), null, 50).get(0).unreadCount()).isZero();
    }

    @Test
    void un_usuario_solo_ve_sus_conversaciones() {
        open(1, CLIENT, OFFERER, "PENDING", T0);
        assertThat(listIds(999, ConversationFilter.none())).isEmpty();
    }

    @Test
    void filtra_por_estado_de_la_conversacion_por_estado_de_la_solicitud_y_por_ambos_combinados() {
        long pending = open(1, CLIENT, 21, "PENDING", T0).id();
        long accepted = open(2, CLIENT, 22, "ACCEPTED", T0.plusSeconds(1)).id();
        long cancelled = open(3, CLIENT, 23, "CANCELLED", T0.plusSeconds(2)).id();   // nace READ_ONLY
        long completed = open(4, CLIENT, 24, "COMPLETED", T0.plusSeconds(3)).id();   // nace READ_ONLY

        // por estado de la conversación
        assertThat(listIds(CLIENT, filter(Set.of(ConversationStatus.ACTIVE), Set.of())))
                .containsExactlyInAnyOrder(pending, accepted);
        assertThat(listIds(CLIENT, filter(Set.of(ConversationStatus.READ_ONLY), Set.of())))
                .containsExactlyInAnyOrder(cancelled, completed);
        // por estado de la solicitud (un valor y varios)
        assertThat(listIds(CLIENT, filter(Set.of(), Set.of("ACCEPTED")))).containsExactly(accepted);
        assertThat(listIds(CLIENT, filter(Set.of(), Set.of("PENDING", "COMPLETED"))))
                .containsExactlyInAnyOrder(pending, completed);
        // ambos combinados
        assertThat(listIds(CLIENT, filter(Set.of(ConversationStatus.ACTIVE), Set.of("ACCEPTED", "CANCELLED"))))
                .containsExactly(accepted);
        assertThat(listIds(CLIENT, filter(Set.of(ConversationStatus.READ_ONLY), Set.of("PENDING")))).isEmpty();
        // varios estados de conversación a la vez = todos
        assertThat(listIds(CLIENT, filter(Set.of(ConversationStatus.ACTIVE, ConversationStatus.READ_ONLY), Set.of())))
                .hasSize(4);
    }

    @Test
    void el_filtro_por_tipo_se_aplica() {
        open(1, CLIENT, OFFERER, "PENDING", T0);
        assertThat(readModel.findForUser(CLIENT, new ConversationFilter(Set.of(), Set.of(),
                Set.of(com.parosurvivors.chat.conversations.domain.ConversationType.REQUEST)), null, 50)).hasSize(1);
        assertThat(readModel.findForUser(CLIENT, new ConversationFilter(Set.of(), Set.of(),
                Set.of(com.parosurvivors.chat.conversations.domain.ConversationType.DIRECT)), null, 50)).isEmpty();
    }

    @Test
    void los_valores_de_filtro_se_enlazan_como_parametros_y_no_se_interpolan() {
        open(1, CLIENT, OFFERER, "PENDING", T0);
        assertThat(listIds(CLIENT, filter(Set.of(), Set.of("PENDING') OR 1=1 --")))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversations", Integer.class)).isEqualTo(1);
    }

    @Test
    void la_paginacion_por_cursor_recorre_todo_sin_repetir_ni_saltar_incluso_con_empates() {
        Instant sameInstant = T0.plusSeconds(100);
        for (int i = 1; i <= 5; i++) {
            open(i, CLIENT, 100 + i, "PENDING", sameInstant);   // empate en last_message_at: desempata el id
        }
        java.util.ArrayList<Long> seen = new java.util.ArrayList<>();
        ConversationCursor cursor = null;
        for (int page = 0; page < 10; page++) {
            List<ConversationSummary> rows = readModel.findForUser(CLIENT, ConversationFilter.none(), cursor, 2);
            if (rows.isEmpty()) {
                break;
            }
            rows.forEach(r -> seen.add(r.id()));
            ConversationSummary last = rows.get(rows.size() - 1);
            cursor = new ConversationCursor(last.lastMessageAt(), last.id());
        }
        assertThat(seen).hasSize(5).doesNotHaveDuplicates().isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void una_conversacion_archivada_por_el_usuario_no_aparece_pero_si_para_el_otro() {
        long id = open(1, CLIENT, OFFERER, "PENDING", T0).id();
        jdbc.update("UPDATE conversation_participants SET archived_at = NOW(6) WHERE conversation_id = ? AND user_id = ?",
                id, CLIENT);
        assertThat(listIds(CLIENT, ConversationFilter.none())).isEmpty();
        assertThat(listIds(OFFERER, ConversationFilter.none())).containsExactly(id);
    }
}
