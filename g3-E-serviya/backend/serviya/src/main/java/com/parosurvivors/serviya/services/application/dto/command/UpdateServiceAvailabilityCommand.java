package com.parosurvivors.serviya.services.application.dto.command;

import java.time.LocalTime;

public record UpdateServiceAvailabilityCommand (
    Long id,
    byte weekDay,
    LocalTime startTime,
    LocalTime endTime,
    boolean isActive
){}
