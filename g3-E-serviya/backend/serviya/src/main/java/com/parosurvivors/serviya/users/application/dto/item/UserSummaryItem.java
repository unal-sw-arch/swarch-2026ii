package com.parosurvivors.serviya.users.application.dto.item;

import java.time.LocalDateTime;

/**
 * Salida de aplicacion (Item) del resumen de un usuario en el listado paginado del panel admin
 * (GET /api/v1/admin/users, RF-068). Vista agregada CQRS-light que NO pasa por una unica entidad de
 * dominio: enriquece la cuenta ({@code users}) con el nombre y la foto del perfil ({@code user_profiles}).
 * La arma la query nativa de {@code UserReadAdapter} (@ConstructorResult -> constructor canonico del record).
 */
public record UserSummaryItem(
        Long id,
        String email,
        String fullName,
        String photoUrl,
        Boolean banned,
        LocalDateTime deletedAt,
        LocalDateTime createdAt) {
}
