package com.parosurvivors.serviya.users.application.ports.input;

import com.parosurvivors.serviya.users.domain.Role;

import java.util.List;

/**
 * Puerto de entrada de RoleService.
 * Ver documents/project-structure/estructura-servicios.docx (módulo 1).
 */
public interface RoleServicePort {

    List<Role> getRoles();
}
