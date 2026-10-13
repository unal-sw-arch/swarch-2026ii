package com.parosurvivors.serviya.users.infrastructure.adapters.output;

import com.parosurvivors.serviya.users.application.ports.output.RolePersistencePort;
import com.parosurvivors.serviya.users.domain.Role;
import com.parosurvivors.serviya.users.domain.RoleName;
import com.parosurvivors.serviya.users.infrastructure.mappers.RolePersistenceMapper;
import com.parosurvivors.serviya.users.infrastructure.repositories.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RolePersistenceAdapter implements RolePersistencePort {

    private final RoleRepository repository;
    private final RolePersistenceMapper mapper;

    @Override
    public List<Role> findAll() {
        return repository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Role> findById(Integer id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Role> findByName(RoleName name) {
        return repository.findByName(name).map(mapper::toDomain);
    }
}
