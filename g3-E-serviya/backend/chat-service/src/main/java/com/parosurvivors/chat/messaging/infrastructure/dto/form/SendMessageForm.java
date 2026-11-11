package com.parosurvivors.chat.messaging.infrastructure.dto.form;

/** Cuerpo de {@code POST /conversations/{id}/messages}. La validación de fondo es del dominio. */
public record SendMessageForm(String clientMessageId, String body) {
}
