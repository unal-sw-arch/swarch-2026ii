package com.parosurvivors.serviya.users.infrastructure.adapters.output;

import com.parosurvivors.serviya.users.application.ports.output.RefreshTokenPort;
import com.parosurvivors.serviya.users.domain.RefreshToken;
import com.parosurvivors.serviya.users.infrastructure.mappers.RefreshTokenPersistenceMapper;
import com.parosurvivors.serviya.users.infrastructure.repositories.RefreshTokenRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenPersistenceAdapter implements RefreshTokenPort {

    private final RefreshTokenRepository repository;
    private final RefreshTokenPersistenceMapper mapper;

    @Override
    public RefreshToken save(RefreshToken token) {
        return mapper.toDomain(repository.save(mapper.toEntity(token)));
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    public int markReplaced(Long oldTokenId, Long newTokenId) {
        return repository.markReplaced(oldTokenId, newTokenId);
    }

    @Override
    public int revokeFamily(String familyId, LocalDateTime revokedAt) {
        return repository.revokeFamily(familyId, revokedAt);
    }

    @Override
    public int revokeAllForUser(Long userId, LocalDateTime revokedAt) {
        return repository.revokeAllForUser(userId, revokedAt);
    }

    @Override
    public int deleteExpiredOrRevokedBefore(LocalDateTime cutoff) {
        return repository.deleteExpiredOrRevokedBefore(cutoff);
    }
}
