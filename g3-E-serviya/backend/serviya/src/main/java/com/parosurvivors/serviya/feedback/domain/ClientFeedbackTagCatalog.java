package com.parosurvivors.serviya.feedback.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Etiqueta del catálogo para reseñas de clientes (con sentimiento P/N).
 * Mapea la tabla {@code client_feedback_tags_catalog}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientFeedbackTagCatalog {
    private Long id;
    private String tagName;
    private TagSentiment sentiment;

    // =====================================================
    // BUSINESS METHODS
    // =====================================================

    public boolean isPositive() {
        return sentiment != null && sentiment.isPositive();
    }
}
