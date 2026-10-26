package com.parosurvivors.notifications.application.ports.output;

import com.parosurvivors.notifications.domain.Notification;

import java.util.List;
import java.util.Optional;

public interface NotificationPersistencePort {
    Notification save(Notification notification);
    Optional<Notification> findById(Long id);
    List<Notification> findAllById(List<Long> ids);
    boolean existsBySourceEventId(String sourceEventId);
}
