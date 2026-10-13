package com.parosurvivors.serviya.users.infrastructure.repositories;

import com.parosurvivors.serviya.users.infrastructure.entities.ConsentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConsentRepository extends JpaRepository<ConsentEntity, Long> {
    Optional<ConsentEntity> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
}
