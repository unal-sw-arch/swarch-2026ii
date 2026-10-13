package com.parosurvivors.serviya.users.application.ports.output;

import com.parosurvivors.serviya.users.domain.User;

/**
 * Puerto de salida de ESCRITURA de usuarios. Las lecturas (findById/findByEmail/existsByEmail/busqueda)
 * viven en {@link UserReadPort} (split CQRS, igual que el modulo requests).
 */
public interface UserPersistencePort {
    User save(User user);
    User update(User user);
    void deleteById(Long id);
}
