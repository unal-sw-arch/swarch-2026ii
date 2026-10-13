package com.parosurvivors.serviya.requests.application.dto.item;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Salida de aplicacion (Item) del resumen de una solicitud en un listado paginado
 * (mis solicitudes como cliente / como oferente). Vista agregada CQRS-light que NO pasa por una
 * unica entidad de dominio: enriquece con el servicio (titulo/categoria), la contraparte
 * (oferente en la lista del cliente, cliente en la del oferente) y la ciudad de la direccion.
 * La arma la query nativa de {@code ServiceRequestReadAdapter}. La direccion completa (cifrada)
 * NO se expone aqui; solo la ciudad como etiqueta (ver el detalle para la direccion descifrada).
 */
public record ServiceRequestSummaryItem(
        Long requestId,
        String status,
        LocalDateTime scheduledDate,
        BigDecimal requestedPrice,
        Long previousRequestId,
        LocalDateTime createdAt,
        Long serviceId,
        String serviceTitle,
        String categoryName,
        Long counterpartyId,
        String counterpartyName,
        String counterpartyPhotoUrl,
        String city) {
}
