package com.parosurvivors.serviya.users.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Entrada web (Form) para comprobar si un enlace de recuperacion sigue vigente. RF-003.
 * POST /api/v1/auth/password-reset/validate.
 *
 * <p>El token viaja en el BODY y no en el query string, igual que en {@link ConfirmPasswordResetForm}:
 * los parametros de consulta acaban de forma rutinaria en los access logs del servidor y de cualquier
 * proxy intermedio, y este token es una credencial que basta por si sola para tomar la cuenta. Que la
 * operacion sea de solo lectura (un POST que no modifica nada) es el precio que se paga por no
 * escribirla en disco en cada peticion.</p>
 *
 * <p>Existe como DTO — pese a envolver un unico escalar — precisamente por eso: un cuerpo JSON necesita
 * un objeto, y de paso el token queda cubierto por Bean Validation y documentado en el schema.</p>
 */
@Schema(description = "Comprobacion de vigencia de un enlace de recuperacion")
public record ValidatePasswordResetTokenForm(
        @NotBlank
        @Schema(description = "Token recibido en el enlace del correo", requiredMode = Schema.RequiredMode.REQUIRED)
        String token) {
}
