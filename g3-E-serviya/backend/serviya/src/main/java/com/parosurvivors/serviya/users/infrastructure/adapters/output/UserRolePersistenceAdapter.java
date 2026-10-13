package com.parosurvivors.serviya.users.infrastructure.adapters.output;

import com.parosurvivors.serviya.users.application.ports.output.UserRolePersistencePort;
import com.parosurvivors.serviya.users.domain.RoleAssignment;
import com.parosurvivors.serviya.users.infrastructure.entities.UserRoleEntity;
import com.parosurvivors.serviya.users.infrastructure.repositories.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserRolePersistenceAdapter implements UserRolePersistencePort {

    private final UserRoleRepository repository;

    @Override
    public void assignRole(Long userId, Integer roleId) {
        if (repository.existsByUserIdAndRoleId(userId, roleId)) {
            return;
        }
        UserRoleEntity entity = new UserRoleEntity();
        entity.setUserId(userId);
        entity.setRoleId(roleId);
        entity.setAssignedAt(LocalDateTime.now());
        repository.save(entity);
    }

    @Override
    public void removeRole(Long userId, Integer roleId) {
        repository.findByUserIdAndRoleId(userId, roleId).ifPresent(repository::delete);
    }

    @Override
    public boolean existsByUserIdAndRoleId(Long userId, Integer roleId) {
        return repository.existsByUserIdAndRoleId(userId, roleId);
    }

    @Override
    public List<Integer> findRoleIdsByUserId(Long userId) {
        return repository.findByUserId(userId).stream()
                .map(UserRoleEntity::getRoleId)
                .collect(Collectors.toList());
    }

    @Override
    public List<RoleAssignment> findAssignmentsByUserId(Long userId) {
        return repository.findByUserId(userId).stream()
                .map(entity -> new RoleAssignment(entity.getRoleId(), entity.getAssignedAt()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> findUserIdsByRoleId(Integer roleId) {
        return repository.findByRoleId(roleId).stream()
                .map(UserRoleEntity::getUserId)
                .collect(Collectors.toList());
    }
}
