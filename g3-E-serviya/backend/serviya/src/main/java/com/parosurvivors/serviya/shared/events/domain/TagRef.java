package com.parosurvivors.serviya.shared.events.domain;

import com.parosurvivors.serviya.feedback.domain.TagSentiment;

/**
 * Referencia autocontenida a una etiqueta dentro de un evento de feedback: el id del catálogo
 * más su sentimiento (P/N) ya resuelto por el módulo feedback antes de publicar. Permite que los
 * listeners de métricas sumen a totales positivos/negativos y actualicen los conteos por tag sin
 * consultar el catálogo (ver estructura-servicios.docx, módulo 6, "Diseño del evento").
 *
 * <p>El acople es de solo lectura sobre el enum {@link TagSentiment} de feedback (decisión de
 * diseño: se evita duplicar el enum en shared).</p>
 */
public record TagRef(Long tagId, TagSentiment sentiment) {

    public boolean isPositive() {
        return sentiment != null && sentiment.isPositive();
    }
}
