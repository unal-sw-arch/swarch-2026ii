package com.parosurvivors.serviya.reports.application.dto.command;

/**
 * Entrada de aplicacion (Command) para reportar una resena de servicio. El reporterId proviene del JWT.
 */
public record CreateServiceFeedbackReportCommand(
        Long reporterId,
        Long reportedUserId,
        String category,
        String reason,
        Long serviceFeedbackId) {
}
