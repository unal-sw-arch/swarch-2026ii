package com.parosurvivors.serviya.users.application.dto.command;

/**
 * Entrada de aplicacion (Command) del caso de uso cambiar correo.
 * El userId proviene del JWT (lo inyecta el controller), nunca del body.
 */
public record ChangeEmailCommand(
        Long userId,
        String newEmail) {
}
