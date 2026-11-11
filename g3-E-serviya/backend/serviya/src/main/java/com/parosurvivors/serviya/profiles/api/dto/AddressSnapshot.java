package com.parosurvivors.serviya.profiles.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vista publica de una direccion.
 */
public record AddressSnapshot(
        Long id,
        Long userId,
        String addressLine,
        String city,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDateTime createdAt) {
}
