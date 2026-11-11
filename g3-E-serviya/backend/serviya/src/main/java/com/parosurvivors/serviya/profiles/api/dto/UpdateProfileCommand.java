package com.parosurvivors.serviya.profiles.api.dto;

/**
 * Edicion parcial del perfil personal. Los campos nulos no se modifican.
 */
public record UpdateProfileCommand(
        Long userId,
        String fullName,
        String phone,
        String photoUrl,
        String description) {
}
