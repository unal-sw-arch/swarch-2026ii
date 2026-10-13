package com.parosurvivors.serviya.services.domain;

import java.time.LocalTime;

import jakarta.persistence.PrePersist;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceAvailability {
    private Long id;
    private Long serviceId;
    private byte weekDay;        // Cambio 1
    private LocalTime startTime;
    private LocalTime endTime;
    private boolean isActive;    // Cambio 2

    public static void checkServiceWeekDay(Integer weekDay) {
        if (!(weekDay >= 1 && weekDay <= 7)) {
            throw new IllegalArgumentException("Service is not available on this day");
        }
    }
    
    // Cambio 3: Validar en setter
    @PrePersist
    public void validateBeforePersist() {
        checkServiceWeekDay((int) weekDay);
    }
}