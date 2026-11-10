package com.parosurvivors.serviya.requests.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Filtros de busqueda de solicitudes. Campos {@code null}/vacios = filtro no aplicado.
 */
public record RequestSearchCriteria(
        List<String> statuses,
        Long serviceId,
        Long categoryId,
        Long counterpartyId,
        String titleQuery,
        LocalDateTime scheduledFrom,
        LocalDateTime scheduledTo,
        LocalDateTime createdFrom,
        LocalDateTime createdTo) {

    public RequestSearchCriteria {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
    }
}
