package com.parosurvivors.serviya.users.infrastructure.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Salida web (Response) de autenticacion. Devuelta por register y login con el JWT (modulo 1).
 * TODO: revisar campos (p. ej. expiracion, roles emitidos en el token).
 */
@Schema(description = "Token de autenticacion emitido tras login/registro")
public record AuthResponse(
        @Schema(description = "JWT Bearer") String token,
        @Schema(description = "Tipo de token", example = "Bearer") String tokenType,
        @Schema(description = "Segundos hasta la expiracion") Long expiresIn) {
}
