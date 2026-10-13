package com.parosurvivors.serviya.users.infrastructure.entities;

import com.parosurvivors.serviya.users.application.dto.item.UserSummaryItem;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * El {@code @SqlResultSetMapping} construye el read-model de listado admin directamente desde las columnas
 * de la query nativa de {@code UserReadAdapter} (ConstructorResult -> constructor canonico del record;
 * sin mapeo posterior). El {@code name} de cada columna casa con el alias del SELECT y el ORDEN debe
 * coincidir con los componentes del record {@link UserSummaryItem}.
 */
@Entity
@Table(name = "users")
@SqlResultSetMapping(
    name = "UserSummaryMapping",
    classes = @ConstructorResult(
        targetClass = UserSummaryItem.class,
        columns = {
            @ColumnResult(name = "id", type = Long.class),
            @ColumnResult(name = "email", type = String.class),
            @ColumnResult(name = "fullName", type = String.class),
            @ColumnResult(name = "photoUrl", type = String.class),
            @ColumnResult(name = "banned", type = Boolean.class),
            @ColumnResult(name = "deletedAt", type = LocalDateTime.class),
            @ColumnResult(name = "createdAt", type = LocalDateTime.class)
        }))
@Getter
@Setter
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "is_banned", nullable = false)
    private Boolean banned;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
