package com.parosurvivors.notifications.application.ports.input;

import com.parosurvivors.notifications.domain.NotificationChannel;

import java.util.List;
import java.util.Optional;

/** Puerto de entrada de NotificationChannelService (catálogo de canales). */
public interface NotificationChannelServicePort {

    List<NotificationChannel> getChannels();

    Optional<NotificationChannel> findByName(String name);
}
