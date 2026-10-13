package com.parosurvivors.serviya.requests.application.ports.output;

import com.parosurvivors.serviya.requests.domain.ServiceRequest;

/**
 * Puerto de salida de PERSISTENCIA (mutaciones) de solicitudes de servicio. Solo escritura;
 * todas las lecturas (finds de dominio, conteos, agenda, mantenimiento y vistas enriquecidas) viven
 * en {@link ServiceRequestReadPort} (mismo split que RescheduleProposalPersistencePort/ReadPort).
 */
public interface ServiceRequestPersistencePort {
    ServiceRequest save(ServiceRequest request);
    ServiceRequest update(ServiceRequest request);
}
