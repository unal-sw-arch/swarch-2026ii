package com.parosurvivors.serviya.services.application.dto.command;

import java.math.BigDecimal;
import java.util.List;

/**
 * Entrada de aplicacion (Command) para crear un servicio. El offererId proviene del JWT.
 */
public record CreateServiceCommand(
        Long offererId,
        String title,
        String description,
        List<String> photos,
        BigDecimal priceHourly,
        Long categoryId,
        Integer averageDurationMinutes,
        BigDecimal operationRadiusKm) {
}
