package com.parosurvivors.serviya.users.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rol asignable a un usuario. Mapea la tabla {@code roles}
 * (id TINYINT UNSIGNED, name ENUM).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {
    private Integer id;
    private RoleName name;
}
