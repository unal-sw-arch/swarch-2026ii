package com.parosurvivors.serviya.shared.events.domain;

/**
 * Se creó una solicitud de servicio ORIGINAL (no un reemplazo de reprogramación). Alimenta los
 * contadores de actividad: {@code ClientMetrics.totalRequestsSent} (cliente que la envió) y
 * {@code OffererMetrics.totalRequestsReceived} (oferente que la recibió). Se publica solo desde
 * {@code createRequest}; los reemplazos generados por reprogramación NO lo publican (esa relación
 * ya se contabiliza como reprogramada, no como nueva solicitud).
 */
public record RequestCreatedEvent(
        Long requestId,
        Long clientId,
        Long offererId,
        Long serviceId) {
}
