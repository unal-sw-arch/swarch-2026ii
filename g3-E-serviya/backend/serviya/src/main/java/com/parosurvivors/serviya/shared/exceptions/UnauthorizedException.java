package com.parosurvivors.serviya.shared.exceptions;

/**
 * Se lanza cuando la peticion no esta autenticada o las credenciales son invalidas
 * (login fallido, cuenta baneada o eliminada, contexto sin usuario). El
 * {@code GlobalExceptionHandler} la traduce a 401 Unauthorized.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
