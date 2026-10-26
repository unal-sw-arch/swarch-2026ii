package com.parosurvivors.notifications.infrastructure.web;

import java.time.LocalDateTime;

/** Cuerpo de error uniforme. MISMO formato que el ErrorResponse del monolito (el frontend no distingue). */
public record ErrorResponse(int status, String error, String message, String path, LocalDateTime timestamp) {
}
