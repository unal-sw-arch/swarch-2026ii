package com.parosurvivors.serviya.admin.application.dto.command;

/**
 * Entrada de aplicacion (Command) para crear un usuario desde el panel admin. El adminId proviene del JWT.
 * Delega en el flujo comun de creacion (UserCreationService.createUserAccount).
 */
public record CreateUserByAdminCommand(
        Long adminId,
        String email,
        String password,
        String fullName,
        String role,
        String documentType,
        String documentNumber,
        String phone) {
}
