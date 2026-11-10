package com.parosurvivors.serviya.feedback.api.dto;

/**
 * Entrada del catalogo de tags de feedback.
 */
public record TagCatalogEntry(Long id, String name, boolean positive) {
}
