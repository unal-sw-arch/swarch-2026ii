package com.parosurvivors.serviya.shared.exceptions;

/**
 * Se lanza cuando el usuario está autenticado pero NO tiene permiso sobre el recurso: reglas de dominio de
 * propiedad/participación ("el oferente solo edita su servicio", "solo el participante ve la solicitud").
 * El {@code GlobalExceptionHandler} la traduce a <b>403 Forbidden</b> (decisión #12 / M10).
 *
 * <p>Se distingue de {@link UnauthorizedException} (401), que es un fallo de <b>autenticación</b> (login
 * inválido, cuenta baneada/eliminada, sin token). El cliente debe refrescar ante 401, pero NO ante 403.</p>
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
