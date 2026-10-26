package com.parosurvivors.serviya.feedback.application.dto.command;

import java.util.List;

/**
 * Entrada de aplicacion (Command) del feedback del oferente a un cliente.
 * offererId proviene del JWT, requestId del path; clientId es el cliente calificado.
 */
public record SubmitClientFeedbackCommand(
        Long offererId,
        Long requestId,
        Long clientId,
        Integer rating,
        String comment,
        List<Long> tagIds) {
}
