package com.parosurvivors.notifications.infrastructure.repositories;

import com.parosurvivors.notifications.infrastructure.entities.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    boolean existsBySourceEventId(String sourceEventId);
}
