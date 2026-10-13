package com.parosurvivors.serviya.shared.events.domain;

import com.parosurvivors.serviya.requests.domain.RequestStatus;

/**
 * Una solicitud de servicio cambió de estado. Lo escuchan {@code OffererMetricsService} y
 * {@code ClientMetricsService}, que incrementan el contador correspondiente al {@code newStatus}
 * (accepted/completed/cancelled/rescheduled/not_provided). Estados sin contador asociado
 * (PENDING, REJECTED, PRESUMABLY_COMPLETED) se ignoran en los listeners.
 *
 * <p>Acople de solo lectura sobre el enum {@link RequestStatus} de requests (misma decisión de
 * diseño que {@link TagRef} con TagSentiment).</p>
 *
 * @param previousStatus estado anterior (útil para trazabilidad; los listeners usan {@code newStatus}).
 */
public record RequestStatusChangedEvent(
        Long requestId,
        Long clientId,
        Long offererId,
        Long serviceId,
        RequestStatus previousStatus,
        RequestStatus newStatus) {
}
