package com.parosurvivors.serviya.users.application.ports.output;

import com.parosurvivors.serviya.users.domain.Role;
import com.parosurvivors.serviya.users.domain.RoleName;

import java.util.List;
import java.util.Optional;

public interface RolePersistencePort {
    List<Role> findAll();
    Optional<Role> findById(Integer id);
    Optional<Role> findByName(RoleName name);
}
