package com.parosurvivors.serviya.services.application.dto.command;

import java.math.BigDecimal;
import java.util.List;

/**
 * Entrada de aplicacion (Command) para actualizar parcialmente un servicio. El serviceId va en el path;
 * la verificacion de propiedad la hace el servicio. Campos no-nulos = a actualizar.
 */
public record UpdateServiceCommand(
        Long serviceId,
        String title,
        String description,
        List<String> photos,
        BigDecimal priceHourly,
        Long categoryId,
        Integer averageDurationMinutes,
        BigDecimal operationRadiusKm) {
}
