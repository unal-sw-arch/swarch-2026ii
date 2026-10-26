package com.parosurvivors.serviya.admin.application.dto.command;

/**
 * Entrada de aplicacion (Command) para eliminar directamente una resena inapropiada (admin).
 * El adminId proviene del JWT. Sustituye al antiguo placeholder CreateReportRequest.
 */
public record RemoveFeedbackCommand(
        Long adminId,
        String targetType,
        Long targetId,
        Long reportedUserId,
        String category,
        String reason) {
}
