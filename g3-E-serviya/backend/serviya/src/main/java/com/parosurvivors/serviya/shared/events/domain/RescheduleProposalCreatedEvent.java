package com.parosurvivors.serviya.shared.events.domain;

/**
 * El oferente creó una propuesta de reprogramación para una solicitud. Alimenta
 * {@code OffererMetrics.totalRescheduleProposalsSent} (la participación del oferente en el flujo de
 * reprogramación es proponer, no reprogramar). Se publica desde {@code RescheduleProposalService.createProposal}.
 */
public record RescheduleProposalCreatedEvent(
        Long proposalId,
        Long requestId,
        Long offererId,
        Long clientId) {
}
