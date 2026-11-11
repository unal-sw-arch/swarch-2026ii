package com.parosurvivors.serviya.shared.events.domain;

/**
 * Referencia autocontenida a una etiqueta dentro de un evento de feedback: el id del catálogo y su
 * sentimiento ya resuelto por el módulo feedback antes de publicar. Permite que los listeners de
 * métricas sumen a totales positivos/negativos y actualicen los conteos por tag sin consultar el
 * catálogo (ver estructura-servicios.docx, módulo 6, "Diseño del evento").
 *
 * <p>El contrato usa {@code boolean} en lugar del enum interno
 * {@code feedback.domain.TagSentiment}: el evento debe poder serializarse a JSON y viajar por el
 * bus sin acoplarse al dominio del módulo feedback.</p>
 */
public record TagRef(Long tagId, boolean positive) {

    public boolean isPositive() {
        return positive;
    }
}
