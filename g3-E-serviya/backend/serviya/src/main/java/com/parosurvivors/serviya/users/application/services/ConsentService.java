package com.parosurvivors.serviya.users.application.services;

import com.parosurvivors.serviya.users.application.ports.input.ConsentServicePort;
import com.parosurvivors.serviya.users.application.ports.output.ConsentPersistencePort;
import com.parosurvivors.serviya.users.domain.Consent;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Registro del consentimiento de uso de datos personales (RF-004). La validacion de que el
 * consentimiento sea aceptado antes de crear la cuenta vive en UserCreationService; aqui solo
 * se persiste la aceptacion con su fecha/hora.
 */
@Component
@RequiredArgsConstructor
public class ConsentService implements ConsentServicePort {

    private final ConsentPersistencePort consentPersistencePort;

    @Override
    public Consent createConsent(Long userId, boolean accepted) {
        Consent consent = Consent.builder()
                .userId(userId)
                .accepted(accepted)
                .consentedAt(LocalDateTime.now())
                .build();
        return consentPersistencePort.save(consent);
    }

    @Override
    public boolean hasConsented(Long userId) {
        return consentPersistencePort.findByUserId(userId)
                .map(Consent::isAccepted)
                .orElse(false);
    }
}
