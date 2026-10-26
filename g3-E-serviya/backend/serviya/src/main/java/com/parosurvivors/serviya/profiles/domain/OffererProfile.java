package com.parosurvivors.serviya.profiles.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Perfil del usuario en su rol de oferente. Mapea la tabla {@code offerer_profiles} (1:1 con users).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffererProfile {
    private Long id;
    private Long userId;
    private String whatsappNumber;
    private String publicDescription;
    private String specialty;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
