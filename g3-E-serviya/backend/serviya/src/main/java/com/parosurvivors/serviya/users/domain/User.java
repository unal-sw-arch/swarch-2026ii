package com.parosurvivors.serviya.users.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


import com.parosurvivors.serviya.shared.exceptions.InvalidStateException;

/**
 * Cuenta de usuario (credenciales y estado de la cuenta). Mapea la tabla {@code users}.
 * Los roles son una vista de conveniencia (tabla puente {@code user_roles}); no son una
 * columna de esta tabla y el mapper de persistencia los ignora.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    private Long id;
    private String email;
    private String passwordHash;
    @Builder.Default
    private Boolean banned = false;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;

    /** Roles asignados (hidratados desde user_roles); no se persiste como columna de users. */
    @Builder.Default
    private List<RoleName> roles = new ArrayList<>();

    // =====================================================
    // BUSINESS METHODS
    // =====================================================

    public void ban() {
        this.banned = true;
    }

    public void unban() {
        this.banned = false;
    }

    public boolean isBanned() {
        return Boolean.TRUE.equals(banned);
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** La cuenta puede operar: ni baneada ni eliminada. */
    public boolean isActive() {
        return !isBanned() && !isDeleted();
    }

    public void changeEmail(String newEmail) {
        if (newEmail == null || !newEmail.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new InvalidStateException("Invalid email format");
        }
        this.email = newEmail;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public boolean hasRole(RoleName role) {
        return roles != null && roles.contains(role);
    }
}
