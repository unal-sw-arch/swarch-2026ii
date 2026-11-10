package com.parosurvivors.serviya.config.integration;

import com.parosurvivors.serviya.notifications.application.ports.output.RecipientEmailPort;
import com.parosurvivors.serviya.users.api.UserContactApi;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adaptador de integracion que resuelve el correo del destinatario con {@code users.api}. Vive en la
 * raiz de composicion para que notifications no dependa de users: el modulo de notificaciones solo
 * conoce su propio {@link RecipientEmailPort}.
 */
@Component
@RequiredArgsConstructor
public class UserRecipientEmailAdapter implements RecipientEmailPort {

    private final UserContactApi userContactApi;

    @Override
    public Optional<String> findEmail(Long userId) {
        // Un destinatario inexistente no debe tumbar la operación de negocio: la entrega EMAIL quedará FAILED.
        return userContactApi.emailOf(userId).filter(email -> !email.isBlank());
    }
}
