package com.parosurvivors.gateway.error;

/**
 * Cuerpo de error uniforme del gateway (contrato 4.4). El orden de los campos se conserva en el JSON.
 *
 * <pre>{ "status": 401, "error": "unauthorized", "code": "TOKEN_REVOKED", "message": "...", "correlationId": "..." }</pre>
 */
public record GatewayErrorResponse(
        int status,
        String error,
        String code,
        String message,
        String correlationId) {
}
