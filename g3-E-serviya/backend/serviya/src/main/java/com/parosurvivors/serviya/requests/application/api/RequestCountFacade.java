package com.parosurvivors.serviya.requests.application.api;

import com.parosurvivors.serviya.requests.api.RequestCountApi;
import com.parosurvivors.serviya.requests.application.ports.output.ServiceRequestReadPort;
import com.parosurvivors.serviya.requests.domain.RequestStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementacion minima del conteo de solicitudes activas. Solo depende de
 * {@code ServiceRequestReadPort}.
 */
@Component
@RequiredArgsConstructor
public class RequestCountFacade implements RequestCountApi {

    private static final List<RequestStatus> ACTIVE_STATUSES = List.of(
            RequestStatus.PENDING,
            RequestStatus.ACCEPTED,
            RequestStatus.PRESUMABLY_COMPLETED);

    private final ServiceRequestReadPort serviceRequestReadPort;

    @Override
    public long countActiveClientRequests(Long clientId) {
        return serviceRequestReadPort.countByClientIdAndStatusIn(clientId, ACTIVE_STATUSES);
    }
}
