package com.parosurvivors.serviya.feedback.application.dto.command;

import java.util.List;

/**
 * Entrada de aplicacion (Command) del feedback del cliente a un servicio.
 * clientId proviene del JWT, requestId del path.
 */
public record SubmitServiceFeedbackCommand(
        Long clientId,
        Long requestId,
        Integer rating,
        String comment,
        List<Long> tagIds) {
}
