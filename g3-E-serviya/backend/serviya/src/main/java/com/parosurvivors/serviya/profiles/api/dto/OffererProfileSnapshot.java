package com.parosurvivors.serviya.profiles.api.dto;

import java.time.LocalDateTime;

/**
 * Vista publica del perfil de oferente.
 */
public record OffererProfileSnapshot(
        Long id,
        Long userId,
        String whatsappNumber,
        String publicDescription,
        String specialty,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
