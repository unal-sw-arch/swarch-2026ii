package com.parosurvivors.serviya.feedback.domain;

/**
 * Sentimiento de una etiqueta de reseña. Coincide con el ENUM('P','N') de las tablas
 * {@code service_feedback_tags_catalog} y {@code client_feedback_tags_catalog}.
 * Los nombres de las constantes coinciden con los valores almacenados en BD (@Enumerated STRING).
 */
public enum TagSentiment {
    /** Positivo. */
    P,
    /** Negativo. */
    N;

    public boolean isPositive() {
        return this == P;
    }
}
