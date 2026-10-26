package com.parosurvivors.serviya.users.infrastructure.repositories;

import com.parosurvivors.serviya.users.domain.RoleName;
import com.parosurvivors.serviya.users.infrastructure.entities.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, Integer> {
    Optional<RoleEntity> findByName(RoleName name);
}
