package com.parosurvivors.chat.messaging.infrastructure.dto.response;

import java.util.List;

/** Siempre en orden ascendente por seq. Para seguir hacia atrás, el cliente usa {@code before_seq = min(seq)}. */
public record MessagePageResponse(List<MessageResponse> items) {
}
