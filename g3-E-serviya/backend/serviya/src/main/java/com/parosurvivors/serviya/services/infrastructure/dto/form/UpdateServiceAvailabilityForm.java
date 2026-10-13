package com.parosurvivors.serviya.services.infrastructure.dto.form;

import java.time.LocalTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Campos editables de la disponibilidad de un servicio (PATCH parcial)")
public record UpdateServiceAvailabilityForm (

    @Size(min = 0, max = 6) byte weekDay,
    LocalTime startTime,
    LocalTime endTime,
    boolean isActive

){}
