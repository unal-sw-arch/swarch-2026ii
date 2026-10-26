package com.parosurvivors.serviya.users.infrastructure.repositories;

import com.parosurvivors.serviya.users.infrastructure.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
    
    /** Contar usuarios que tienen un rol específico (por nombre de rol). */
    @Query(nativeQuery = true, value = 
        "SELECT COUNT(DISTINCT ur.user_id) FROM user_roles ur " +
        "JOIN roles r ON ur.role_id = r.id " +
        "WHERE r.name = :roleName")
    long countByRolesName(String roleName);
}
