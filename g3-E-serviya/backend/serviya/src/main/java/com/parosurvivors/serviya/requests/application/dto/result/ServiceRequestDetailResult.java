package com.parosurvivors.serviya.requests.application.dto.result;

import com.parosurvivors.serviya.requests.domain.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Salida de aplicacion (Result) del detalle de una solicitud para una de las partes (CQRS-light).
 * Vista agregada que NO pasa por una unica entidad de dominio: la compone
 * ServiceRequestQueryService.getRequestDetailForParty inyectando los puertos de servicio, categoria,
 * perfiles y direccion. A diferencia del resumen de listado, aqui la direccion va DESCIFRADA
 * (addressLine) mas ciudad y coordenadas, para que cliente y oferente coordinen el lugar de prestacion.
 *
 * <p>Muestra solo la CONTRAPARTE (la otra parte relativa a quien consulta), no ambas partes: el que
 * consulta ya es cliente u oferente. Para la vista con ambas partes ver {@link AdminRequestDetailResult}.
 */
public record ServiceRequestDetailResult(
        // Solicitud
        Long id,
        RequestStatus status,
        LocalDateTime scheduledDate,
        BigDecimal requestedPrice,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime updatedStatusAt,
        Long previousRequestId,
        // Servicio
        Long serviceId,
        String serviceTitle,
        String categoryName,
        BigDecimal priceHourly,
        Integer averageDurationMinutes,
        // Contraparte (la otra parte relativa al que consulta)
        Long counterpartyId,
        String counterpartyName,
        String counterpartyPhotoUrl,
        // Ubicacion (direccion descifrada)
        Long addressId,
        String addressLine,
        String city,
        BigDecimal latitude,
        BigDecimal longitude) {
}
