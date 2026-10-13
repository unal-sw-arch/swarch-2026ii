package com.parosurvivors.serviya.requests.application.ports.output;

import com.parosurvivors.serviya.requests.domain.RescheduleProposal;

/**
 * Puerto de salida de PERSISTENCIA (mutaciones) de propuestas de reprogramacion. Solo escritura;
 * todas las lecturas (finds de dominio + vistas enriquecidas) viven en {@link RescheduleProposalReadPort}.
 */
public interface RescheduleProposalPersistencePort {
    RescheduleProposal save(RescheduleProposal proposal);
    RescheduleProposal update(RescheduleProposal proposal);
}
