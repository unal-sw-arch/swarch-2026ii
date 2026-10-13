package com.parosurvivors.notifications.infrastructure.adapters.output;

import com.parosurvivors.notifications.application.ports.output.NotificationDeliveryPersistencePort;
import com.parosurvivors.notifications.domain.DeliveryStatus;
import com.parosurvivors.notifications.domain.NotificationDelivery;
import com.parosurvivors.notifications.infrastructure.entities.NotificationDeliveryEntity;
import com.parosurvivors.notifications.infrastructure.mappers.NotificationDeliveryPersistenceMapper;
import com.parosurvivors.notifications.infrastructure.repositories.NotificationDeliveryRepository;
import com.parosurvivors.notifications.infrastructure.repositories.NotificationDeliverySpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NotificationDeliveryPersistenceAdapter implements NotificationDeliveryPersistencePort {

    private final NotificationDeliveryRepository repository;
    private final NotificationDeliveryPersistenceMapper mapper;

    @Override
    public NotificationDelivery save(NotificationDelivery delivery) {
        NotificationDeliveryEntity saved = repository.save(mapper.toEntity(delivery));
        return mapper.toDomain(saved);
    }

    @Override
    public NotificationDelivery update(NotificationDelivery delivery) {
        NotificationDeliveryEntity updated = repository.save(mapper.toEntity(delivery));
        // Refleja la nueva versión en el objeto de dominio por si el llamador vuelve a actualizarlo.
        delivery.setVersion(updated.getVersion());
        return mapper.toDomain(updated);
    }

    @Override
    public Optional<NotificationDelivery> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<NotificationDelivery> findByIdAndUserId(Long id, Long userId) {
        return repository.findByIdAndUserId(id, userId).map(mapper::toDomain);
    }

    @Override
    public Page<NotificationDelivery> findDeliveriesByUserId(Long userId, Boolean read, Long channelId, DeliveryStatus status, Pageable pageable) {
        return repository.findAll(
                NotificationDeliverySpecification.fromQuery(userId, read, channelId, status),
                pageable
        ).map(mapper::toDomain);
    }

    @Override
    public List<NotificationDelivery> claimForRetry(DeliveryStatus status, int maxAttempts, int limit) {
        return repository.claimForRetry(status.name(), maxAttempts, limit).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
