package com.parosurvivors.serviya.users.infrastructure.repositories;

import com.parosurvivors.serviya.users.infrastructure.entities.UserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRoleEntity, Long> {
    List<UserRoleEntity> findByUserId(Long userId);
    Optional<UserRoleEntity> findByUserIdAndRoleId(Long userId, Integer roleId);
    boolean existsByUserIdAndRoleId(Long userId, Integer roleId);
    /** Titulares de un rol; usado para notificar a la cola de administradores (RF-073). */
    List<UserRoleEntity> findByRoleId(Integer roleId);
}
