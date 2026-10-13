package com.parosurvivors.serviya.requests.application.dto.command;

import java.time.LocalDateTime;

/**
 * Entrada de aplicacion (Command) para crear una propuesta de reprogramacion. El offererId proviene del JWT.
 */
public record CreateRescheduleProposalCommand(
        Long requestId,
        Long offererId,
        String reason,
        LocalDateTime proposedDate) {
}
