package com.parosurvivors.serviya.requests.api;

import com.parosurvivors.serviya.requests.api.dto.RequestSnapshot;
import java.util.Optional;

/**
 * Lectura minima de una solicitud para consumidores que solo necesitan sus datos base.
 *
 * <p>Existe separada de {@link RequestQueryApi} para evitar ciclos de beans: feedback solo necesita
 * {@code findById}, y no debe arrastrar la cadena query-service -> services.api -> marketplace.
 * La implementacion usa unicamente {@code ServiceRequestReadPort}.</p>
 */
public interface RequestLookupApi {

    Optional<RequestSnapshot> findById(Long requestId);
}
