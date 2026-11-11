package com.parosurvivors.serviya.shared.events.domain;

/**
 * Una solicitud de servicio cambió de estado. Lo escuchan {@code OffererMetricsService} y
 * {@code ClientMetricsService}, que incrementan el contador correspondiente al {@code newStatus}
 * (accepted/completed/cancelled/rescheduled/not_provided). Estados sin contador asociado
 * (PENDING, REJECTED, PRESUMABLY_COMPLETED) se ignoran en los listeners.
 *
 * <p>El contrato usa {@code String} para los estados en lugar del enum interno
 * {@code requests.domain.RequestStatus}: el evento debe poder serializarse a JSON y viajar por el
 * bus sin acoplarse al dominio del modulo requests.</p>
 *
 * @param previousStatus estado anterior (útil para trazabilidad; los listeners usan {@code newStatus}).
 */
public record RequestStatusChangedEvent(
        Long requestId,
        Long clientId,
        Long offererId,
        Long serviceId,
        String previousStatus,
        String newStatus) {
}
