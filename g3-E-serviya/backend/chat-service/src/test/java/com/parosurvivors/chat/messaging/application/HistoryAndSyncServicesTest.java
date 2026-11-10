package com.parosurvivors.chat.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parosurvivors.chat.conversations.domain.Conversation;
import com.parosurvivors.chat.conversations.domain.TerminalRequestStatuses;
import com.parosurvivors.chat.messaging.application.services.GetConversationHistoryService;
import com.parosurvivors.chat.messaging.application.services.SyncMessagesService;
import com.parosurvivors.chat.messaging.domain.Message;
import com.parosurvivors.chat.support.Fakes;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HistoryAndSyncServicesTest {

    private static final long CLIENT = 10;
    private static final long OFFERER = 20;
    private static final long STRANGER = 99;
    private static final Instant T0 = Instant.parse("2026-11-09T12:00:00Z");

    private final Fakes.InMemoryConversations conversations = new Fakes.InMemoryConversations();
    private final Fakes.InMemoryMessages messages = new Fakes.InMemoryMessages();
    private final GetConversationHistoryService history = new GetConversationHistoryService(conversations, messages);
    private final SyncMessagesService sync = new SyncMessagesService(conversations, messages);
    private long id;

    @BeforeEach
    void setUp() {
        Conversation c = conversations.save(Conversation.openForRequest(7, CLIENT, OFFERER, "PENDING", T0,
                TerminalRequestStatuses.of("CANCELLED")));
        id = c.id();
        for (int i = 1; i <= 10; i++) {
            c = conversations.findById(id).orElseThrow();
            long seq = c.registerMessage(CLIENT, T0.plusSeconds(i));
            conversations.save(c);
            messages.save(Message.text(id, seq, CLIENT, "m" + i, "mensaje " + i, 2000, T0.plusSeconds(i)));
        }
    }

    private static List<Long> seqs(List<Message> ms) {
        return ms.stream().map(Message::seq).toList();
    }

    // --- historial (RF-CHAT-12/13) ---

    @Test
    void la_primera_pagina_trae_los_mas_recientes_en_orden_ascendente() {
        assertThat(seqs(history.execute(OFFERER, id, null, null, 3))).containsExactly(8L, 9L, 10L);
    }

    @Test
    void paginar_hacia_atras_con_before_seq_no_repite_ni_salta_mensajes() {
        assertThat(seqs(history.execute(OFFERER, id, 8L, null, 3))).containsExactly(5L, 6L, 7L);
        assertThat(seqs(history.execute(OFFERER, id, 5L, null, 3))).containsExactly(2L, 3L, 4L);
        assertThat(seqs(history.execute(OFFERER, id, 2L, null, 3))).containsExactly(1L);
    }

    @Test
    void con_after_seq_trae_lo_posterior() {
        assertThat(seqs(history.execute(OFFERER, id, null, 8L, 50))).containsExactly(9L, 10L);
    }

    @Test
    void before_y_after_a_la_vez_es_invalido() {
        assertThatThrownBy(() -> history.execute(OFFERER, id, 5L, 2L, 10)).extracting("code")
                .isEqualTo("CHAT_INVALID_PAGINATION");
    }

    @Test
    void el_tamano_de_pagina_por_defecto_y_maximo_se_acotan() {
        assertThat(history.execute(OFFERER, id, null, null, null)).hasSize(10);
        assertThat(history.execute(OFFERER, id, null, null, 100_000)).hasSize(10);
    }

    @Test
    void un_tercero_no_puede_leer_el_historial_y_una_conversacion_inexistente_no_se_encuentra() {
        assertThatThrownBy(() -> history.execute(STRANGER, id, null, null, 10)).extracting("code")
                .isEqualTo("CHAT_NOT_PARTICIPANT");
        assertThatThrownBy(() -> history.execute(CLIENT, 999, null, null, 10)).extracting("code")
                .isEqualTo("CHAT_CONVERSATION_NOT_FOUND");
    }

    @Test
    void el_historial_de_una_conversacion_en_solo_lectura_sigue_disponible() {
        Conversation c = conversations.findById(id).orElseThrow();
        c.applyRequestStatus("CANCELLED", T0.plusSeconds(100), TerminalRequestStatuses.of("CANCELLED"));
        conversations.save(c);
        assertThat(history.execute(CLIENT, id, null, null, 50)).hasSize(10);
    }

    // --- sync tras reconectar (RNF-CHAT-16) ---

    @Test
    void sync_devuelve_lo_que_falta_desde_el_ultimo_seq_conocido() {
        var result = sync.execute(OFFERER, List.of(new SyncMessagesService.Cursor(id, 7)));
        assertThat(result).hasSize(1);
        assertThat(seqs(result.get(0).messages())).containsExactly(8L, 9L, 10L);
        assertThat(result.get(0).hasMore()).isFalse();
    }

    @Test
    void sync_omite_las_conversaciones_al_dia_las_ajenas_y_las_inexistentes() {
        var result = sync.execute(OFFERER, List.of(
                new SyncMessagesService.Cursor(id, 10),       // al día
                new SyncMessagesService.Cursor(999, 0)));     // no existe
        assertThat(result).isEmpty();
        assertThat(sync.execute(STRANGER, List.of(new SyncMessagesService.Cursor(id, 0)))).isEmpty(); // ajena
    }

    @Test
    void sync_marca_has_more_cuando_hay_mas_del_limite_por_conversacion() {
        Conversation c = conversations.findById(id).orElseThrow();
        for (int i = 11; i <= 300; i++) {
            long seq = c.registerMessage(CLIENT, T0.plusSeconds(i));
            messages.save(Message.text(id, seq, CLIENT, "m" + i, "mensaje " + i, 2000, T0.plusSeconds(i)));
        }
        conversations.save(c);

        var result = sync.execute(OFFERER, List.of(new SyncMessagesService.Cursor(id, 0)));

        assertThat(result.get(0).messages()).hasSize(SyncMessagesService.PER_CONVERSATION_LIMIT);
        assertThat(result.get(0).hasMore()).isTrue();
        assertThat(result.get(0).messages().get(0).seq()).isEqualTo(1);
    }
}
