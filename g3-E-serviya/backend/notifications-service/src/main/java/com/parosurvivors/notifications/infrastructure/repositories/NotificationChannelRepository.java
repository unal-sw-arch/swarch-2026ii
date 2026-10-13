package com.parosurvivors.notifications.infrastructure.repositories;

import com.parosurvivors.notifications.infrastructure.entities.NotificationChannelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationChannelRepository extends JpaRepository<NotificationChannelEntity, Integer> {
    Optional<NotificationChannelEntity> findByName(String name);
}
