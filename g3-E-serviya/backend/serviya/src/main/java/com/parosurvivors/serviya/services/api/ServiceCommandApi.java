package com.parosurvivors.serviya.services.api;

import com.parosurvivors.serviya.services.api.dto.AvailabilityTemplateSlot;
import java.util.List;

/**
 * Comandos publicos del modulo services.
 */
public interface ServiceCommandApi {

    /** Desactiva todos los servicios de un oferente (flujo de baja de cuenta). */
    void deactivateAllByOfferer(Long offererId);

    /** Borrado de un servicio por un administrador. */
    void deleteServiceAsAdmin(Long serviceId, Long adminId);

    /** Reemplaza las franjas del servicio por la plantilla resuelta por el BFF. */
    void applyAvailabilityTemplate(Long serviceId, List<AvailabilityTemplateSlot> slots);
}
