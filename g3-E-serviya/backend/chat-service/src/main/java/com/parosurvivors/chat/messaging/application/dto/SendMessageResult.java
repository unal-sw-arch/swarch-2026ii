package com.parosurvivors.chat.messaging.application.dto;

import com.parosurvivors.chat.messaging.domain.Message;
import java.util.List;

/** Contenido del {@code message.ack}. {@code duplicate} = reenvío reconocido por client_message_id (RF-CHAT-16). */
public record SendMessageResult(Message message, List<String> warnings, boolean duplicate) {
}
