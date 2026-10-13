package com.parosurvivors.serviya.shared.textfilter.application.ports.output;

/**
 * Puerto de salida (hexagonal) del filtro de palabras (RNF-006). Todo campo de texto libre
 * (descripciones de perfil, motivos de reporte, reseñas...) pasa por aquí ANTES de persistir,
 * sin que la capa de aplicación conozca la implementación concreta (lista configurable, servicio
 * externo, etc.).
 *
 * <p>El adaptador actual censura las palabras bloqueadas en vez de rechazar la operación: así el
 * usuario no pierde su contenido y el dato queda limpio en base de datos.</p>
 */
public interface WordFilterPort {

    /**
     * Devuelve el texto con las palabras bloqueadas censuradas. Es null-safe: si {@code text} es
     * null devuelve null (útil para los PATCH parciales, donde un campo ausente no se toca).
     */
    String filter(String text);

    /** Indica si el texto contiene alguna palabra bloqueada (útil para validaciones/tests). */
    boolean containsBlockedWords(String text);
}
