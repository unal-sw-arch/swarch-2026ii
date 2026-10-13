package com.parosurvivors.serviya.profiles.application.ports.input;

import com.parosurvivors.serviya.profiles.application.dto.result.OffererPublicProfileResult;

/**
 * Puerto de entrada del agregado PUBLICO del oferente (RF-027).
 *
 * <p>Vive en un servicio aparte de {@code OffererProfileServicePort} a proposito: componer la vitrina
 * publica exige leer los servicios del oferente ({@code MarketplaceServicePort}), pero el marketplace
 * ya depende de {@code OffererProfileServicePort} para enriquecer cada servicio con su oferente. Si el
 * mismo bean hiciera ambas cosas se formaria un ciclo de dependencias y el contexto de Spring no
 * arrancaria. Con este servicio separado la dependencia va en una sola direccion:
 * {@code OffererPublicProfileService -> MarketplaceService -> OffererProfileService}.</p>
 */
public interface OffererPublicProfileServicePort {

    /**
     * RF-027: perfil publico completo (identidad, especialidad, reputacion, metricas de desempeño y
     * servicios activos). Accesible sin autenticacion; no expone PII sensible.
     */
    OffererPublicProfileResult getPublicProfileDetail(Long userId);
}
