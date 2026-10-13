package com.parosurvivors.serviya.profiles.application.dto.command;

/**
 * Entrada de aplicacion (Command) para actualizar parcialmente el perfil de oferente.
 * El userId proviene del JWT. Campos no-nulos = a actualizar.
 */
public record UpdateOffererProfileCommand(
        Long userId,
        String whatsappNumber,
        String publicDescription,
        String specialty) {
}
