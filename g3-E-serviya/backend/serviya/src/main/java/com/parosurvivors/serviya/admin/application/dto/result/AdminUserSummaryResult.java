package com.parosurvivors.serviya.admin.application.dto.result;

import java.time.LocalDateTime;

/**
 * Fila del listado de usuarios del panel admin. Compone la cuenta ({@code users.api}) con el
 * nombre y la foto del perfil ({@code profiles.api}).
 */
public record AdminUserSummaryResult(
        Long id,
        String email,
        String fullName,
        String photoUrl,
        Boolean banned,
        LocalDateTime deletedAt,
        LocalDateTime createdAt) {
}
