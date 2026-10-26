package com.parosurvivors.serviya.notifications.infrastructure.adapters.output;

import com.parosurvivors.serviya.notifications.application.ports.output.RecipientEmailPort;
import com.parosurvivors.serviya.shared.exceptions.ResourceNotFoundException;
import com.parosurvivors.serviya.users.application.ports.input.UserQueryServicePort;
import com.parosurvivors.serviya.users.domain.User;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resuelve el correo del destinatario a través del puerto de ENTRADA de users (no de su persistencia, M8).
 * Es la única dependencia que queda del cliente de notificaciones hacia otro módulo, y vive en el monolito:
 * notifications-service recibe el correo ya resuelto.
 */
@Component
@RequiredArgsConstructor
public class UserRecipientEmailAdapter implements RecipientEmailPort {

    private final UserQueryServicePort userQueryServicePort;

    @Override
    public Optional<String> findEmail(Long userId) {
        try {
            return Optional.ofNullable(userQueryServicePort.getUserById(userId))
                    .map(User::getEmail)
                    .filter(email -> !email.isBlank());
        } catch (ResourceNotFoundException ex) {
            // Un destinatario inexistente no debe tumbar la operación de negocio: la entrega EMAIL quedará FAILED.
            return Optional.empty();
        }
    }
}
