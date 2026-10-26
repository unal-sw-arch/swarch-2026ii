package com.parosurvivors.serviya.profiles.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Franja de disponibilidad general del oferente (plantilla semanal).
 * Mapea la tabla {@code offerer_availabilities}. {@code weekDay} va de 0 (domingo) a 6.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffererAvailability {
    private Long id;
    private Long offererId;
    // Weekday is an integer with an ID for day, 0 for monday 6 for sunday
    private Integer weekDay;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean active;

    // =====================================================
    // BUSINESS METHODS
    // =====================================================

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    /** La franja es coherente si el inicio es estrictamente anterior al fin. */
    public boolean isValidRange() {
        return startTime != null && endTime != null && startTime.isBefore(endTime);
    }

    /** Indica si una hora del mismo día cae dentro de esta franja activa. */
    public boolean covers(LocalTime time) {
        return isActive() && isValidRange()
                && !time.isBefore(startTime) && time.isBefore(endTime);
    }

    /** Indica si esta franja se solapa con otra del mismo día de la semana. */
    public boolean overlaps(OffererAvailability other) {
        if (other == null || !weekDay.equals(other.weekDay)) {
            return false;
        }
        return startTime.isBefore(other.endTime) && other.startTime.isBefore(endTime);
    }
}
