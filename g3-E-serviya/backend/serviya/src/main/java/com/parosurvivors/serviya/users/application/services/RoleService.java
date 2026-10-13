package com.parosurvivors.serviya.users.application.services;

import com.parosurvivors.serviya.users.application.ports.input.RoleServicePort;
import com.parosurvivors.serviya.users.application.ports.output.RolePersistencePort;
import com.parosurvivors.serviya.users.domain.Role;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementacion placeholder de RoleServicePort.
 * Metodos sin logica aun (lanzan UnsupportedOperationException); dependencias inyectadas.
 * Ver documents/project-structure/estructura-servicios.docx.
 */
@Component
@RequiredArgsConstructor
public class RoleService implements RoleServicePort {

    private final RolePersistencePort rolePersistencePort;

    @Override
    public List<Role> getRoles() {
        return rolePersistencePort.findAll();
    }
}
