package com.parosurvivors.serviya.profiles.application.dto.result;

import java.math.BigDecimal;
import java.util.List;

/**
 * Salida de aplicacion (Result) del perfil PUBLICO de un oferente (RF-027). Vista agregada que no
 * corresponde a una sola entidad: combina {@code user_profiles} (nombre y foto), {@code offerer_profiles}
 * (especialidad, descripcion publica, whatsapp), {@code offerer_metrics} (reputacion y desempeño, RF-053)
 * y sus servicios ACTIVOS.
 *
 * <p>Es publico: lo consultan clientes, administradores y visitantes SIN sesion. Por eso no expone
 * ningun dato PII sensible (ni documento ni telefono personal); el whatsapp si, porque es el canal de
 * contacto que el propio oferente publica.</p>
 */
public record OffererPublicProfileResult(
        Long userId,
        // Identidad publica
        String fullName,
        String profilePhotoUrl,
        String specialty,
        String publicDescription,
        String whatsappNumber,
        // Reputacion (RF-053)
        BigDecimal averageRating,
        Integer totalRatings,
        Integer totalComments,
        Integer totalPositiveTags,
        Integer totalNegativeTags,
        // Desempeño
        Integer totalCompletedServices,
        Integer totalCancelledServices,
        Integer totalNotProvidedServices,
        // Servicios publicados y activos (enlazan a su pagina de descripcion, RF-028)
        List<PublishedService> services) {

    /** Resumen de un servicio activo del oferente, suficiente para pintar la tarjeta y enlazar al detalle. */
    public record PublishedService(
            Long id,
            String title,
            String description,
            BigDecimal priceHourly,
            String categoryName,
            Integer averageDurationMinutes) {
    }
}
