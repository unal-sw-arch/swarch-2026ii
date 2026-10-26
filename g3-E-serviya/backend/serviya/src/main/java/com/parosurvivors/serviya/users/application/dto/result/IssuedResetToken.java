package com.parosurvivors.serviya.users.application.dto.result;

import java.time.LocalDateTime;

/**
 * Salida de {@code PasswordResetTokenServicePort.createToken}: el token de recuperacion EN CLARO
 * y su expiracion. Analogo a {@link IssuedToken} (emision del JWT).
 *
 * <p>Es el unico momento de todo el sistema en que el token existe en claro: de la tabla solo sale
 * su hash SHA-256. Vive en memoria lo justo para que el caso de uso arme el enlace del correo; no
 * se persiste, no se loguea y jamas viaja en una Response.</p>
 */
public record IssuedResetToken(
        String rawToken,
        LocalDateTime expiresAt) {
}
