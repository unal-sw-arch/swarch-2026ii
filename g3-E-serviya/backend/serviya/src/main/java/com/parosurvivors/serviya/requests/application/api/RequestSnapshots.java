package com.parosurvivors.serviya.requests.application.api;

import com.parosurvivors.serviya.requests.api.dto.RequestSnapshot;
import com.parosurvivors.serviya.requests.domain.ServiceRequest;

/**
 * Mapeo compartido entre las fachadas de lectura de requests.
 */
final class RequestSnapshots {

    private RequestSnapshots() {
    }

    static RequestSnapshot toSnapshot(ServiceRequest request) {
        return new RequestSnapshot(
                request.getId(),
                request.getServiceId(),
                request.getPreviousRequestId(),
                request.getClientId(),
                request.getOffererId(),
                request.getAddressId(),
                request.getScheduledDate(),
                request.getStatus() == null ? null : request.getStatus().name(),
                request.getRequestedPrice(),
                request.getCreatedAt(),
                request.getCompletedAt(),
                request.getUpdatedStatusAt());
    }
}
