package com.parosurvivors.serviya.users.application.dto.result;

import com.parosurvivors.serviya.users.domain.RoleName;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Salida de aplicacion (Result) del flujo de autenticacion. Lo que devuelven los casos de uso
 * login/register: el token emitido y metadatos. NO es un tipo web; el WebMapper lo traduce a AuthResponse.
 * TODO: revisar campos.
 */
public record AuthResult(
        String token,
        Long userId,
        List<RoleName> roles,
        LocalDateTime expiresAt) {
}
