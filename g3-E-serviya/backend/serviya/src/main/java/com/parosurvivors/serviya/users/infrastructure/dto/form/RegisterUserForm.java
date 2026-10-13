package com.parosurvivors.serviya.users.infrastructure.dto.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Entrada web (Form) de registro de un nuevo usuario. POST /api/v1/auth/register (RF-002, RF-004).
 * Agrupa credenciales + datos de perfil + rol deseado + consentimiento; el servicio delega la
 * creacion comun en UserCreationService.createUserAccount.
 * TODO: revisar campos exactos y validaciones contra documentacion-BD.docx.
 */
@Schema(description = "Datos de registro de un nuevo usuario (CLIENT u OFFERER)")
public record RegisterUserForm(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String fullName,
        // Rol deseado: CLIENT u OFFERER (nunca ADMIN). Va en el body, no en query.
        @NotBlank String role,
        // TODO datos de perfil (documentacion-BD.docx): tipo/numero de documento, telefono.
        @NotBlank String documentType,
        @NotBlank String documentNumber,
        String phone,
        @NotNull Boolean acceptedTerms,

        // --- Direccion principal (opcional) ---
        // Si se envia, se crea en la MISMA transaccion del registro y queda como direccion principal,
        // de modo que el usuario entra con su direccion ya cargada en "Mis direcciones".
        // Los cuatro campos van juntos: la tabla addresses exige coordenadas (lat/lng NOT NULL), asi que
        // una linea de direccion sin coordenadas no es persistible. Si falta alguno, se ignora el bloque.
        @Schema(description = "Direccion (se cifra con AES-256-GCM)") String addressLine,
        String city,
        BigDecimal latitude,
        BigDecimal longitude) {
}
