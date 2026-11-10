package com.parosurvivors.chat.messaging.infrastructure.dto.response;

import com.parosurvivors.chat.messaging.application.dto.SendMessageResult;
import java.util.List;

/** Respuesta del envío por REST: equivale al {@code message.ack} del WebSocket. */
public record SendMessageResponse(MessageResponse message, List<String> warnings, boolean duplicate) {

    public static SendMessageResponse of(SendMessageResult r) {
        return new SendMessageResponse(MessageResponse.of(r.message()), r.warnings(), r.duplicate());
    }
}
