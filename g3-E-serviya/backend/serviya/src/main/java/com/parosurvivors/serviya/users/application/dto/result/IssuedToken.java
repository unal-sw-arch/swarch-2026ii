package com.parosurvivors.serviya.users.application.dto.result;

import java.time.LocalDateTime;

/**
 * Salida del puerto de emision de tokens ({@code TokenProviderPort}). Contiene el token
 * firmado y su instante de expiracion. Es un tipo de aplicacion (no web): el caso de uso
 * lo traduce a {@link AuthResult} y el WebMapper a AuthResponse. Ver RF-001/RF-002.
 */
public record IssuedToken(
        String token,
        LocalDateTime expiresAt) {
}
