package com.parosurvivors.serviya.users.api.dto;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Vista publica de una cuenta de usuario. No expone el hash de contrasena ni la entidad JPA.
 */
public record UserSnapshot(
        Long id,
        String email,
        boolean banned,
        boolean deleted,
        LocalDateTime deletedAt,
        LocalDateTime createdAt,
        Set<String> roles) {

    public UserSnapshot {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
