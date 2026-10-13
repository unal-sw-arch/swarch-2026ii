package com.parosurvivors.serviya.users.application.dto.result;

/**
 * Resultado de validar un token de recuperación de contraseña.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 1 - PasswordResetTokenService).
 */
public enum TokenValidationResult {
    VALID,
    USED,
    EXPIRED,
    NOT_FOUND
}
