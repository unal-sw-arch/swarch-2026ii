package com.parosurvivors.serviya.services.application.dto.command;

import java.time.LocalTime;

public record CreateServiceAvailabilityCommand (
    Long serviceId,
    byte weekDay,
    LocalTime startTime,
    LocalTime endTime,
    boolean isActive
){}
