package com.parosurvivors.serviya.reports.application.dto.command;

/**
 * Entrada de aplicacion (Command) para reportar una solicitud. El reporterId proviene del JWT.
 */
/**
 * RF-073. No lleva reportedUserId a proposito: el usuario reportado se deriva de la contraparte de la
 * solicitud, nunca lo elige quien reporta (si no, podria incriminar a un tercero).
 */
public record CreateRequestReportCommand(
        Long reporterId,
        String category,
        String reason,
        Long requestId) {
}
