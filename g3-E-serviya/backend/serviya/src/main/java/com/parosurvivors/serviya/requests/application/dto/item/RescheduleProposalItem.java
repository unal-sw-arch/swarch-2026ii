package com.parosurvivors.serviya.requests.application.dto.item;

import java.time.LocalDateTime;

/**
 * Salida de aplicacion (Item) del resumen de una propuesta de reprogramacion en un listado
 * (recibidas/enviadas). Vista agregada CQRS-light que NO pasa por una unica entidad de dominio:
 * enriquece con la fecha original de la solicitud, el titulo del servicio y la contraparte
 * (oferente en "recibidas", cliente en "enviadas"). La arma la query nativa del read adapter.
 */
public record RescheduleProposalItem(
        Long proposalId,
        String status,
        LocalDateTime originalScheduledDate,
        LocalDateTime proposedDate,
        String serviceTitle,
        String counterpartyName,
        String counterpartyPhotoUrl,
        LocalDateTime createdAt) {
}
