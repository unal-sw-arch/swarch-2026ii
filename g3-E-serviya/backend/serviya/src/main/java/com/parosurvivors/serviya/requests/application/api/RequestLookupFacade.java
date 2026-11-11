package com.parosurvivors.serviya.requests.application.api;

import com.parosurvivors.serviya.requests.api.RequestLookupApi;
import com.parosurvivors.serviya.requests.api.dto.RequestSnapshot;
import com.parosurvivors.serviya.requests.application.ports.output.ServiceRequestReadPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementacion minima de lectura de requests. Solo depende de {@code ServiceRequestReadPort},
 * por lo que puede ser consumida por modulos que tambien participan en la cadena de servicios sin
 * crear ciclos de beans.
 */
@Component
@RequiredArgsConstructor
public class RequestLookupFacade implements RequestLookupApi {

    private final ServiceRequestReadPort serviceRequestReadPort;

    @Override
    public Optional<RequestSnapshot> findById(Long requestId) {
        return serviceRequestReadPort.findById(requestId).map(RequestSnapshots::toSnapshot);
    }
}
