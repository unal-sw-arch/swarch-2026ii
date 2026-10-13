package com.parosurvivors.notifications.application.exceptions;

/** No hay un usuario autenticado en el contexto. Se traduce a 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
