package com.parosurvivors.serviya.shared.security;

import org.slf4j.MDC;

/**
 * ID de correlacion de la peticion actual. Se propaga en el header {@code X-Request-Id} y se usa
 * en los eventos de integracion.
 */
public final class CorrelationContext {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationContext() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
