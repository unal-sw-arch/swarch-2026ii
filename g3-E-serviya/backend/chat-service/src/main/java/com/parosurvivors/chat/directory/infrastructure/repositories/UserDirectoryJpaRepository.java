package com.parosurvivors.chat.directory.infrastructure.repositories;

import com.parosurvivors.chat.directory.infrastructure.entities.UserDirectoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDirectoryJpaRepository extends JpaRepository<UserDirectoryEntity, Long> {
}
