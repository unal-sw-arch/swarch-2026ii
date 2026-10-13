package com.parosurvivors.serviya.users.application.ports.output;

import com.parosurvivors.serviya.users.domain.Consent;

import java.util.Optional;

public interface ConsentPersistencePort {
    Consent save(Consent consent);
    Optional<Consent> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
}
