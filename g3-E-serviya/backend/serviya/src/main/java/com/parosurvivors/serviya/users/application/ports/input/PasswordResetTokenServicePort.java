package com.parosurvivors.serviya.users.application.ports.input;

import com.parosurvivors.serviya.users.application.dto.result.IssuedResetToken;
import com.parosurvivors.serviya.users.application.dto.result.TokenValidationResult;
import com.parosurvivors.serviya.users.domain.PasswordResetToken;

import java.time.LocalDateTime;

/**
 * Puerto de entrada de PasswordResetTokenService — ciclo de vida del token de recuperación (RF-003).
 * Ver documents/project-structure/estructura-servicios.docx (módulo 1).
 *
 * <p>Regla transversal: el token en claro NO se persiste (solo su SHA-256) y el {@code userId} nunca
 * lo aporta el cliente — se deduce del token encontrado en la tabla.</p>
 */
public interface PasswordResetTokenServicePort {

    /**
     * Mensaje ÚNICO de rechazo de un token, compartido por todos los motivos posibles (no existe,
     * expiró, ya se usó) y por todos los puntos que lo rechazan.
     *
     * <p>Vive en el contrato, y no en cada implementación, precisamente para que no puedan divergir:
     * en cuanto un motivo tuviera un texto distinto, el mensaje pasaría a revelar el estado interno
     * de los tokens (si existió, si caducó, si alguien ya lo usó).</p>
     */
    String GENERIC_INVALID_TOKEN_MESSAGE = "Invalid or expired password reset token";

    /**
     * Emite un token nuevo para el usuario e invalida los que tuviera vivos (uno utilizable a la vez).
     * Devuelve el valor en claro: es la única oportunidad de leerlo.
     */
    IssuedResetToken createToken(Long userId);

    /**
     * Verificación de solo lectura, para pintar el formulario al abrir el enlace. NO consume el token
     * (algunos clientes de correo previsualizan enlaces y lo quemarían antes de que el usuario llegue).
     *
     * <p>Nota de diseño: la firma del docx recibía también un {@code userId}. Se eliminó a propósito —
     * confiar en un id que manda el cliente permitiría validar un token contra otra cuenta.</p>
     */
    TokenValidationResult validateToken(String rawToken);

    /**
     * Verificación real y consumo, en un solo paso atómico: valida, marca el token como usado y quema
     * los demás del usuario. Devuelve el token consumido (de ahí sale el {@code userId} de confianza).
     *
     * @throws com.parosurvivors.serviya.shared.exceptions.InvalidStateException si no existe, expiró o
     *         ya fue usado — con un mensaje genérico único, para no distinguir los tres casos.
     */
    PasswordResetToken consumeToken(String rawToken);

    void markTokenAsUsed(Long tokenId);

    /** Limpieza programada de tokens ya expirados. Devuelve cuántos borró. */
    int deleteExpiredTokens(LocalDateTime cutoff);
}
