package com.parosurvivors.serviya.requests.api;

/**
 * Conteo minimo de solicitudes activas de un cliente.
 *
 * <p>Existe separado de {@link RequestQueryApi} para evitar ciclos de beans: metrics solo necesita
 * este conteo y no debe arrastrar query-service -> services.api -> marketplace.</p>
 */
public interface RequestCountApi {

    long countActiveClientRequests(Long clientId);
}
