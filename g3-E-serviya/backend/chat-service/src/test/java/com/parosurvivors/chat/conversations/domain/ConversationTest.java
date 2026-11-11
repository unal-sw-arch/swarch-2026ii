package com.parosurvivors.chat.conversations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConversationTest {

    private static final long CLIENT = 10;
    private static final long OFFERER = 20;
    private static final long STRANGER = 99;
    private static final Instant T0 = Instant.parse("2026-11-09T12:00:00Z");
    private static final TerminalRequestStatuses TERMINAL =
            TerminalRequestStatuses.of("REJECTED", "CANCELLED", "COMPLETED", "RESCHEDULED", "NOT_PROVIDED");

    private static Conversation open() {
        return Conversation.openForRequest(7, CLIENT, OFFERER, "PENDING", T0, TERMINAL);
    }

    // --- apertura ---

    @Test
    void al_crearse_la_solicitud_nace_activa_con_un_cliente_y_un_oferente() {
        Conversation c = open();
        assertThat(c.type()).isEqualTo(ConversationType.REQUEST);
        assertThat(c.contextId()).isEqualTo(7);
        assertThat(c.status()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(c.contextStatus()).isEqualTo("PENDING");
        assertThat(c.lastSeq()).isZero();
        assertThat(c.lastMessageAt()).isEqualTo(T0);
        assertThat(c.participant(CLIENT).orElseThrow().role()).isEqualTo(ParticipantRole.CLIENT);
        assertThat(c.participant(OFFERER).orElseThrow().role()).isEqualTo(ParticipantRole.OFFERER);
        assertThat(c.participants()).hasSize(2);
    }

    @Test
    void cliente_y_oferente_no_pueden_ser_el_mismo_usuario() {
        assertThatThrownBy(() -> Conversation.openForRequest(7, CLIENT, CLIENT, "PENDING", T0, TERMINAL))
                .isInstanceOf(ConversationException.class);
    }

    @Test
    void si_la_solicitud_ya_llega_terminal_la_conversacion_nace_en_solo_lectura() {
        Conversation c = Conversation.openForRequest(7, CLIENT, OFFERER, "CANCELLED", T0, TERMINAL);
        assertThat(c.status()).isEqualTo(ConversationStatus.READ_ONLY);
    }

    // --- autorización ---

    @Test
    void un_tercero_no_puede_leer_ni_escribir() {
        Conversation c = open();
        assertThatThrownBy(() -> c.assertCanRead(STRANGER)).extracting("code").isEqualTo("CHAT_NOT_PARTICIPANT");
        assertThatThrownBy(() -> c.assertCanWrite(STRANGER)).extracting("code").isEqualTo("CHAT_NOT_PARTICIPANT");
        assertThatThrownBy(() -> c.registerMessage(STRANGER, T0)).isInstanceOf(ConversationException.class);
    }

    @Test
    void a_un_tercero_no_se_le_revela_que_la_conversacion_esta_en_solo_lectura() {
        Conversation c = open();
        c.applyRequestStatus("CANCELLED", T0.plusSeconds(1), TERMINAL);
        assertThatThrownBy(() -> c.assertCanWrite(STRANGER)).extracting("code").isEqualTo("CHAT_NOT_PARTICIPANT");
    }

    // --- secuencia y lectura ---

    @Test
    void la_secuencia_crece_de_uno_en_uno_sin_huecos() {
        Conversation c = open();
        assertThat(c.registerMessage(CLIENT, T0.plusSeconds(1))).isEqualTo(1);
        assertThat(c.registerMessage(OFFERER, T0.plusSeconds(2))).isEqualTo(2);
        assertThat(c.registerMessage(CLIENT, T0.plusSeconds(3))).isEqualTo(3);
        assertThat(c.lastSeq()).isEqualTo(3);
        assertThat(c.lastMessageAt()).isEqualTo(T0.plusSeconds(3));
    }

    @Test
    void el_emisor_no_cuenta_su_propio_mensaje_como_no_leido_pero_el_otro_si() {
        Conversation c = open();
        c.registerMessage(CLIENT, T0.plusSeconds(1));
        c.registerMessage(CLIENT, T0.plusSeconds(2));
        assertThat(c.unreadFor(CLIENT)).isZero();
        assertThat(c.unreadFor(OFFERER)).isEqualTo(2);
    }

    @Test
    void marcar_leido_avanza_pero_nunca_retrocede_ni_supera_el_ultimo_seq() {
        Conversation c = open();
        c.registerMessage(CLIENT, T0.plusSeconds(1));
        c.registerMessage(CLIENT, T0.plusSeconds(2));
        c.registerMessage(CLIENT, T0.plusSeconds(3));

        assertThat(c.markRead(OFFERER, 2)).isEqualTo(Optional.of(2L));
        assertThat(c.markRead(OFFERER, 1)).isEmpty();          // retroceso: sin cambio
        assertThat(c.markRead(OFFERER, 2)).isEmpty();          // repetido: sin cambio
        assertThat(c.markRead(OFFERER, 500)).isEqualTo(Optional.of(3L)); // acotado al último
        assertThat(c.unreadFor(OFFERER)).isZero();
    }

    @Test
    void un_tercero_no_puede_marcar_leido() {
        assertThatThrownBy(() -> open().markRead(STRANGER, 1)).isInstanceOf(ConversationException.class);
    }

    // --- estado de la solicitud y solo lectura ---

    @Test
    void estado_terminal_pasa_a_solo_lectura_y_nadie_puede_escribir() {
        Conversation c = open();
        c.registerMessage(CLIENT, T0.plusSeconds(1));

        assertThat(c.applyRequestStatus("CANCELLED", T0.plusSeconds(10), TERMINAL)).isTrue();

        assertThat(c.status()).isEqualTo(ConversationStatus.READ_ONLY);
        assertThat(c.contextStatus()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> c.registerMessage(CLIENT, T0.plusSeconds(11)))
                .extracting("code").isEqualTo("CHAT_CONVERSATION_READ_ONLY");
        assertThatThrownBy(() -> c.registerMessage(OFFERER, T0.plusSeconds(11)))
                .extracting("code").isEqualTo("CHAT_CONVERSATION_READ_ONLY");
        // El historial sigue siendo consultable.
        c.assertCanRead(CLIENT);
        c.assertCanRead(OFFERER);
    }

    @Test
    void estados_no_terminales_actualizan_el_estado_copiado_sin_cerrar() {
        Conversation c = open();
        assertThat(c.applyRequestStatus("ACCEPTED", T0.plusSeconds(5), TERMINAL)).isTrue();
        assertThat(c.contextStatus()).isEqualTo("ACCEPTED");
        assertThat(c.status()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(c.applyRequestStatus("PRESUMABLY_COMPLETED", T0.plusSeconds(6), TERMINAL)).isTrue();
        assertThat(c.status()).isEqualTo(ConversationStatus.ACTIVE);
    }

    @Test
    void solo_lectura_es_definitivo_un_estado_posterior_no_terminal_no_reabre() {
        Conversation c = open();
        c.applyRequestStatus("REJECTED", T0.plusSeconds(5), TERMINAL);
        c.applyRequestStatus("ACCEPTED", T0.plusSeconds(6), TERMINAL);
        assertThat(c.status()).isEqualTo(ConversationStatus.READ_ONLY);
    }

    @Test
    void evento_fuera_de_orden_mas_viejo_se_ignora() {
        Conversation c = open();
        c.applyRequestStatus("ACCEPTED", T0.plusSeconds(10), TERMINAL);

        assertThat(c.applyRequestStatus("PENDING", T0.plusSeconds(5), TERMINAL)).isFalse();
        assertThat(c.contextStatus()).isEqualTo("ACCEPTED");
        // Un evento viejo que fuera terminal tampoco cierra la conversación.
        assertThat(c.applyRequestStatus("CANCELLED", T0.plusSeconds(4), TERMINAL)).isFalse();
        assertThat(c.status()).isEqualTo(ConversationStatus.ACTIVE);
    }

    @Test
    void el_mismo_evento_repetido_no_cambia_nada() {
        Conversation c = open();
        c.applyRequestStatus("ACCEPTED", T0.plusSeconds(10), TERMINAL);
        assertThat(c.applyRequestStatus("ACCEPTED", T0.plusSeconds(10), TERMINAL)).isFalse();
    }

    @Test
    void los_estados_terminales_se_comparan_sin_importar_mayusculas() {
        assertThat(TERMINAL.isTerminal("cancelled")).isTrue();
        assertThat(TERMINAL.isTerminal("PENDING")).isFalse();
        assertThat(TERMINAL.isTerminal(null)).isFalse();
    }

    // --- mensajes de sistema ---

    @Test
    void mensaje_de_sistema_consume_seq_pero_no_mueve_la_lectura_de_nadie() {
        Conversation c = open();
        assertThat(c.registerMessage(null, T0.plusSeconds(1))).isEqualTo(1);
        assertThat(c.unreadFor(CLIENT)).isEqualTo(1);
        assertThat(c.unreadFor(OFFERER)).isEqualTo(1);
    }

    @Test
    void mensaje_de_sistema_tampoco_se_escribe_en_solo_lectura() {
        Conversation c = open();
        c.applyRequestStatus("CANCELLED", T0.plusSeconds(1), TERMINAL);
        assertThatThrownBy(() -> c.registerMessage(null, T0.plusSeconds(2)))
                .extracting("code").isEqualTo("CHAT_CONVERSATION_READ_ONLY");
    }
}
