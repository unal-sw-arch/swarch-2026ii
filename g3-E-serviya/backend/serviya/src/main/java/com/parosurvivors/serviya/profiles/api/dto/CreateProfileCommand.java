package com.parosurvivors.serviya.profiles.api.dto;

/**
 * Comando publico para crear el perfil personal de una cuenta.
 */
public record CreateProfileCommand(
        Long userId,
        String fullName,
        String documentType,
        String documentNumber,
        String phoneNumber,
        String profileType) {
}
