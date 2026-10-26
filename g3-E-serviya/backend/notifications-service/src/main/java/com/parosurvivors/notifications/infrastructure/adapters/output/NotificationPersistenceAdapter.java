package com.parosurvivors.notifications.infrastructure.adapters.output;

import com.parosurvivors.notifications.application.ports.output.NotificationPersistencePort;
import com.parosurvivors.notifications.domain.Notification;
import com.parosurvivors.notifications.infrastructure.entities.NotificationEntity;
import com.parosurvivors.notifications.infrastructure.mappers.NotificationPersistenceMapper;
import com.parosurvivors.notifications.infrastructure.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NotificationPersistenceAdapter implements NotificationPersistencePort {

    private final NotificationRepository repository;
    private final NotificationPersistenceMapper mapper;

    @Override
    public Notification save(Notification notification) {
        NotificationEntity saved = repository.save(mapper.toEntity(notification));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Notification> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Notification> findAllById(List<Long> ids) {
        return repository.findAllById(ids).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsBySourceEventId(String sourceEventId) {
        return repository.existsBySourceEventId(sourceEventId);
    }
}
