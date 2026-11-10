package com.parosurvivors.serviya.users.api.dto;

import java.time.LocalDateTime;

/**
 * Resumen de cuenta para listados (panel de administracion).
 *
 * <p>Solo contiene datos propios de identity. El nombre/foto del perfil los compone el consumidor
 * (BFF) con {@code profiles.api}; asi identity no depende de catalog.</p>
 */
public record UserSummary(
        Long id,
        String email,
        boolean banned,
        LocalDateTime deletedAt,
        LocalDateTime createdAt) {
}
