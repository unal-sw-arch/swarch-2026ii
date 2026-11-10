package com.parosurvivors.notifications.infrastructure.web;

import java.time.LocalDateTime;

/**
 * Cuerpo de error uniforme (contrato 4.4). MISMO formato que el ErrorResponse del monolito: añade
 * {@code code} (el frontend decide por él) y {@code correlationId} (de {@code X-Request-Id}); conserva
 * {@code path}/{@code timestamp} por compatibilidad.
 */
public record ErrorResponse(int status, String error, String code, String message, String correlationId,
                            String path, LocalDateTime timestamp) {
}
