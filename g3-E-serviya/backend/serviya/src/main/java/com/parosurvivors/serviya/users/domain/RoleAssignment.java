package com.parosurvivors.serviya.users.domain;

import java.time.LocalDateTime;

/**
 * Asignacion de un rol a un usuario: la fila de la tabla puente {@code user_roles}.
 *
 * <p>{@link Role} solo describe el catalogo de roles (id + nombre) y no sabe CUANDO se concedio.
 * Esa fecha vive en la tabla puente y es justamente lo que RF-067 exige mostrar, de ahi este modelo.</p>
 *
 * @param roleId     rol concedido
 * @param assignedAt momento en que se concedio
 */
public record RoleAssignment(Integer roleId, LocalDateTime assignedAt) {
}
