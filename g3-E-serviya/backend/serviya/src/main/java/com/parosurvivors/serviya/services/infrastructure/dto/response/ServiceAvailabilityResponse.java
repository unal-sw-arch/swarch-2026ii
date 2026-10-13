package com.parosurvivors.serviya.services.infrastructure.dto.response;


import java.time.LocalTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Representacion de la disponibilidad de un servicio")
public record ServiceAvailabilityResponse (
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    Long id,
    byte weekDay,
    LocalTime startTime,
    LocalTime endTime,
    boolean activeStatus
){}
