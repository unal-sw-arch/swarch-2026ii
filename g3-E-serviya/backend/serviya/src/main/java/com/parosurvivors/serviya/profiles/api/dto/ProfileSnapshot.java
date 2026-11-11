package com.parosurvivors.serviya.profiles.api.dto;

import java.time.LocalDateTime;

/**
 * Vista publica del perfil personal de un usuario.
 *
 * <p>Incluye datos PII (documento y telefono) porque son propiedad de profiles; cualquier
 * exposicion publica debe pasar por un BFF que aplique la politica de privacidad.</p>
 */
public record ProfileSnapshot(
        Long id,
        Long userId,
        String fullName,
        String documentType,
        String documentNumber,
        String phoneNumber,
        Long primaryAddressId,
        String profilePhotoUrl,
        String bio,
        String profileType,
        LocalDateTime createdAt) {
}
