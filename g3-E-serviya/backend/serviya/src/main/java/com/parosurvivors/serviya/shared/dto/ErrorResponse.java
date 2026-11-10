package com.parosurvivors.serviya.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Cuerpo de error del servicio, alineado con el contrato 4.4 del plan de auth
 * ({@code status, error, code, message, correlationId}). Se conservan {@code path} y {@code timestamp}
 * (compatibilidad con los clientes/tests previos). El {@code code} lo consume el frontend para decidir
 * (p. ej. refrescar ante 401, no ante 403); el {@code correlationId} correlaciona con los logs y lo
 * propaga el gateway por {@code X-Correlation-Id}.
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
public class ErrorResponse {
    private int status;
    private String error;
    private String code;
    private String message;
    private String correlationId;
    private String path;
    private LocalDateTime timestamp;
}
