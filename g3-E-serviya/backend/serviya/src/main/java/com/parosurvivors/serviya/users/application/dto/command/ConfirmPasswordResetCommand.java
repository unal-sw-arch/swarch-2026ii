package com.parosurvivors.serviya.users.application.dto.command;

/**
 * Entrada de aplicacion (Command) del caso de uso confirmar recuperacion de contrasena.
 */
public record ConfirmPasswordResetCommand(
        String token,
        String newPassword) {
}
