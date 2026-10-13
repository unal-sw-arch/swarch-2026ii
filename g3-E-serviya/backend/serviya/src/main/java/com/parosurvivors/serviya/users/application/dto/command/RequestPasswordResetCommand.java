package com.parosurvivors.serviya.users.application.dto.command;

/**
 * Entrada de aplicacion (Command) del caso de uso solicitar recuperacion de contrasena.
 */
public record RequestPasswordResetCommand(
        String email) {
}
