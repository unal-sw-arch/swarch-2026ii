package com.parosurvivors.serviya.admin.application.dto.command;

/**
 * Entrada de aplicacion (Command) para que el administrador edite un usuario (RF-068).
 * Semantica PATCH: solo los campos no-nulos se actualizan.
 *
 * <p>No incluye documentType/documentNumber: el documento es inmutable en todo el sistema (se fija en
 * el registro), tampoco para el admin. El telefono se cifra al persistir (AES-256-GCM) y los textos
 * libres pasan por el filtro de palabras, porque la edicion reutiliza el caso de uso de RF-006.</p>
 */
public record UpdateUserByAdminCommand(
        String email,
        String fullName,
        String phone,
        String photoUrl,
        String description) {
}
