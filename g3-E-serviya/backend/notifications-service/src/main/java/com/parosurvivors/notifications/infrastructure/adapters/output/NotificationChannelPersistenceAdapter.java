package com.parosurvivors.notifications.infrastructure.adapters.output;

import com.parosurvivors.notifications.application.ports.output.NotificationChannelPersistencePort;
import com.parosurvivors.notifications.domain.NotificationChannel;
import com.parosurvivors.notifications.infrastructure.mappers.NotificationChannelPersistenceMapper;
import com.parosurvivors.notifications.infrastructure.repositories.NotificationChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NotificationChannelPersistenceAdapter implements NotificationChannelPersistencePort {

    private final NotificationChannelRepository repository;
    private final NotificationChannelPersistenceMapper mapper;

    @Override
    public List<NotificationChannel> findAll() {
        return repository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<NotificationChannel> findById(Integer id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<NotificationChannel> findByName(String name) {
        return repository.findByName(name).map(mapper::toDomain);
    }
}
