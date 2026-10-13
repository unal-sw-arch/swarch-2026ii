package com.parosurvivors.serviya.users.application.services;

import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.users.application.dto.item.UserSummaryItem;
import com.parosurvivors.serviya.users.application.dto.query.SearchUsersQuery;
import com.parosurvivors.serviya.users.application.ports.input.UserQueryServicePort;
import com.parosurvivors.serviya.users.application.ports.output.RolePersistencePort;
import com.parosurvivors.serviya.users.application.ports.output.UserReadPort;
import com.parosurvivors.serviya.users.domain.Role;
import com.parosurvivors.serviya.users.domain.RoleName;
import com.parosurvivors.serviya.users.domain.User;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Servicio de lectura de usuarios (CQRS). La busqueda enriquecida se resuelve en el adaptador de lectura
 * (query nativa); este servicio traduce el filtro de rol (nombre -> id, consulta unica sobre roles) para
 * que la busqueda no tenga que unir la tabla roles, y mantiene el limite hexagonal (admin -> puerto de entrada).
 * Ver documents/project-structure/estructura-servicios.docx (modulo 9).
 */
@Component
@RequiredArgsConstructor
public class UserQueryService implements UserQueryServicePort {

    private final UserReadPort userReadPort;
    private final RolePersistencePort rolePersistencePort;

    @Override
    public Page<UserSummaryItem> searchUsers(SearchUsersQuery query, Pageable pageable) {
        Integer roleId = null;
        if (query.role() != null && !query.role().isBlank()) {
            RoleName roleName;
            try {
                roleName = RoleName.valueOf(query.role().strip().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return Page.empty(pageable); // rol inexistente: ningun usuario lo tiene.
            }
            Optional<Role> role = rolePersistencePort.findByName(roleName);
            if (role.isEmpty()) {
                return Page.empty(pageable);
            }
            roleId = role.get().getId();
        }
        return userReadPort.searchUsers(query, roleId, pageable);
    }

    @Override
    public User getUserById(Long id) {
        return userReadPort.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }
}
