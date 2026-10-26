package com.parosurvivors.serviya.users.infrastructure.adapters.output;

import com.parosurvivors.serviya.users.application.ports.output.ConsentPersistencePort;
import com.parosurvivors.serviya.users.domain.Consent;
import com.parosurvivors.serviya.users.infrastructure.entities.ConsentEntity;
import com.parosurvivors.serviya.users.infrastructure.mappers.ConsentPersistenceMapper;
import com.parosurvivors.serviya.users.infrastructure.repositories.ConsentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ConsentPersistenceAdapter implements ConsentPersistencePort {

    private final ConsentRepository repository;
    private final ConsentPersistenceMapper mapper;

    @Override
    public Consent save(Consent consent) {
        ConsentEntity saved = repository.save(mapper.toEntity(consent));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Consent> findByUserId(Long userId) {
        return repository.findByUserId(userId).map(mapper::toDomain);
    }

    @Override
    public boolean existsByUserId(Long userId) {
        return repository.existsByUserId(userId);
    }
}
