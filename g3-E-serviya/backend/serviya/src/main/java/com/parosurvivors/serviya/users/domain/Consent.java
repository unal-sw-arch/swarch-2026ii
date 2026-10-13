package com.parosurvivors.serviya.users.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Consentimiento de tratamiento de datos del usuario. Mapea la tabla {@code consents}
 * (relación 1:1 con users).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Consent {
    private Long id;
    private Long userId;
    private Boolean accepted;
    private LocalDateTime consentedAt;

    // =====================================================
    // BUSINESS METHODS
    // =====================================================

    public boolean isAccepted() {
        return Boolean.TRUE.equals(accepted);
    }
}
