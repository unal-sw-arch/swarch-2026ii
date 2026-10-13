package com.parosurvivors.notifications.application.ports.output;

import com.parosurvivors.notifications.domain.NotificationChannel;

import java.util.List;
import java.util.Optional;

public interface NotificationChannelPersistencePort {
    List<NotificationChannel> findAll();
    Optional<NotificationChannel> findById(Integer id);
    Optional<NotificationChannel> findByName(String name);
}
