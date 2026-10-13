package com.parosurvivors.serviya.users.infrastructure.adapters.output;

import com.parosurvivors.serviya.users.application.ports.output.PasswordResetTokenPersistencePort;
import com.parosurvivors.serviya.users.domain.PasswordResetToken;
import com.parosurvivors.serviya.users.infrastructure.entities.PasswordResetTokenEntity;
import com.parosurvivors.serviya.users.infrastructure.mappers.PasswordResetTokenPersistenceMapper;
import com.parosurvivors.serviya.users.infrastructure.repositories.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PasswordResetTokenPersistenceAdapter implements PasswordResetTokenPersistencePort {

    private final PasswordResetTokenRepository repository;
    private final PasswordResetTokenPersistenceMapper mapper;

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        PasswordResetTokenEntity saved = repository.save(mapper.toEntity(token));
        return mapper.toDomain(saved);
    }

    @Override
    public PasswordResetToken update(PasswordResetToken token) {
        PasswordResetTokenEntity updated = repository.save(mapper.toEntity(token));
        return mapper.toDomain(updated);
    }

    @Override
    public Optional<PasswordResetToken> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<PasswordResetToken> findByUserId(Long userId) {
        return repository.findByUserId(userId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    public int invalidateAllForUser(Long userId, LocalDateTime usedAt) {
        return repository.markAllAsUsedByUserId(userId, usedAt);
    }

    @Override
    public int deleteExpiredBefore(LocalDateTime cutoff) {
        return repository.deleteByExpiresAtBefore(cutoff);
    }
}
