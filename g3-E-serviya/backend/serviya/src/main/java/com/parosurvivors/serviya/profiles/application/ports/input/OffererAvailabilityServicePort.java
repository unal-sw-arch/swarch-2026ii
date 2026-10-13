package com.parosurvivors.serviya.profiles.application.ports.input;

import com.parosurvivors.serviya.profiles.application.dto.command.SetAvailabilitySlotCommand;
import com.parosurvivors.serviya.profiles.domain.OffererAvailability;

import java.util.List;

/**
 * Puerto de entrada de OffererAvailabilityService — plantilla de disponibilidad general del oferente.
 * Recibe Commands y devuelve dominio; nunca tipos web.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 2).
 */
public interface OffererAvailabilityServicePort {

    List<OffererAvailability> getSchedule(Long offererId);

    void setSchedule(Long offererId, List<SetAvailabilitySlotCommand> slots);

    void deleteSlot(Long slotId, Long requesterId, boolean isAdmin);

    void activateSlot(Long slotId, Long requesterId, boolean isAdmin);

    void deactivateSlot(Long slotId, Long requesterId, boolean isAdmin);
}
