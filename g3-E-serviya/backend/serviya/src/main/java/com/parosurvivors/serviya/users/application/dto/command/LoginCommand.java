package com.parosurvivors.serviya.users.application.dto.command;

/**
 * Entrada de aplicacion (Command) del caso de uso login. Espejo de LoginForm.
 */
public record LoginCommand(
        String email,
        String password) {
}
