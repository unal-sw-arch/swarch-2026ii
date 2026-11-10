package com.parosurvivors.chat.messaging.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MessageTest {

    private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");

    @Test
    void mensaje_de_texto_se_recorta_y_queda_sin_flags() {
        Message m = Message.text(1, 1, 5, "cid", "  hola  ", 2000, NOW);
        assertThat(m.body()).isEqualTo("hola");
        assertThat(m.kind()).isEqualTo(MessageKind.TEXT);
        assertThat(m.flags()).isEmpty();
        assertThat(m.hasContactInfo()).isFalse();
    }

    @Test
    void mensaje_vacio_o_en_blanco_se_rechaza() {
        assertThatThrownBy(() -> Message.text(1, 1, 5, "cid", "   ", 2000, NOW))
                .isInstanceOf(MessageException.class).extracting("code").isEqualTo("CHAT_MESSAGE_EMPTY");
        assertThatThrownBy(() -> Message.text(1, 1, 5, "cid", null, 2000, NOW))
                .isInstanceOf(MessageException.class);
    }

    @Test
    void mensaje_mas_largo_que_el_maximo_se_rechaza_y_el_limite_exacto_pasa() {
        assertThat(Message.text(1, 1, 5, "cid", "a".repeat(2000), 2000, NOW).body()).hasSize(2000);
        assertThatThrownBy(() -> Message.text(1, 1, 5, "cid", "a".repeat(2001), 2000, NOW))
                .isInstanceOf(MessageException.class).extracting("code").isEqualTo("CHAT_MESSAGE_TOO_LONG");
    }

    @Test
    void mensaje_con_telefono_se_acepta_pero_queda_marcado() {
        Message m = Message.text(1, 1, 5, "cid", "escríbeme al 3001234567", 2000, NOW);
        assertThat(m.hasContactInfo()).isTrue();
        assertThat(m.flags()).containsExactly(MessageFlag.CONTACT_INFO);
    }

    @Test
    void mensaje_de_sistema_no_tiene_emisor_ni_id_de_cliente() {
        Message m = Message.system(1, 2, "La solicitud fue aceptada", NOW);
        assertThat(m.senderId()).isNull();
        assertThat(m.clientMessageId()).isNull();
        assertThat(m.kind()).isEqualTo(MessageKind.SYSTEM);
    }
}
