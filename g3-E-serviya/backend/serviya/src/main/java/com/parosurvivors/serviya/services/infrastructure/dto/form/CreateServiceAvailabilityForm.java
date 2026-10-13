package com.parosurvivors.serviya.services.infrastructure.dto.form;

import java.time.LocalTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Entrada web (Form) para crear una franja de disponibilidad de un servicio.
 * POST /api/v1/service-availabilities/service/{serviceId}.
 */
@Schema(description = "Datos para crear la disponibilidad de un servicio")
public record CreateServiceAvailabilityForm (
    @NotNull Integer weekDay,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @Schema(description = "Si se omite, la franja se crea ACTIVA") Boolean isActive
){
    /**
     * Una franja recien creada esta ACTIVA salvo que se pida explicitamente lo contrario.
     *
     * <p>{@code isActive} es Boolean (admite null) pero el Command lo recibe como boolean primitivo, asi
     * que un null se convertia en {@code false}: la franja se guardaba INACTIVA y, como la consulta de
     * disponibilidad filtra las inactivas, desaparecia de la vista y el cliente no podia reservar en
     * ella. Normalizar aqui el null a true evita ese agujero.</p>
     */
    public CreateServiceAvailabilityForm {
        if (isActive == null) {
            isActive = true;
        }
    }
}
