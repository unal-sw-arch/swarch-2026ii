package com.parosurvivors.serviya.profiles.application.dto.command;

/**
 * Entrada de aplicacion (Command) para actualizar parcialmente el perfil personal.
 * El userId proviene del JWT (lo inyecta el controller). Campos no-nulos = a actualizar.
 */
public record UpdateProfileCommand(
        Long userId,
        String fullName,
        String phone,
        String photoUrl,
        String description) {
}
