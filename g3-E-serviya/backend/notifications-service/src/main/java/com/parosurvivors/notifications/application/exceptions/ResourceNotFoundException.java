package com.parosurvivors.notifications.application.exceptions;

/** El recurso pedido no existe (o no es del usuario autenticado). Se traduce a 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
